package com.madn.dropzone.service;

import com.madn.dropzone.exception.file.EmptyFileException;
import com.madn.dropzone.exception.validation.MethodArgumentNotValidException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StagingSecurityAndValidationTest {

    private StorageService storageService;

    @TempDir
    Path tempStorageDir;

    @BeforeEach
    void setUp() {
        storageService = new StorageService();
        ReflectionTestUtils.setField(storageService, "storageDestination", tempStorageDir);
    }

    @Test
    @DisplayName("Successfully stream file into staging and atomic commit to permanent storage")
    void stagingAndAtomicCommit_Success() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "Valid document content".getBytes()
        );

        Path stagedPath = storageService.stageFile(file);
        assertThat(Files.exists(stagedPath)).isTrue();
        assertThat(stagedPath.startsWith(storageService.getStagingDestination())).isTrue();

        // Validate
        storageService.validateStagedFile(stagedPath, "report.pdf");

        // Calculate checksum
        String checksum = storageService.calculateChecksum(stagedPath);
        assertThat(checksum).isNotBlank();

        // Atomic commit
        String storageKey = storageService.commitStagedFile(stagedPath);
        assertThat(storageKey).isNotBlank();

        // Staged file moved into permanent storage
        Path permanentFile = tempStorageDir.resolve(storageKey);
        assertThat(Files.exists(permanentFile)).isTrue();
        assertThat(Files.readAllBytes(permanentFile)).isEqualTo("Valid document content".getBytes());
        assertThat(Files.exists(stagedPath)).isFalse();
    }

    @Test
    @DisplayName("Reject dangerous executable extension (.exe, .sh, .bat)")
    void validateStagedFile_RejectsDangerousExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "script.sh", "text/plain", "echo hello".getBytes()
        );
        Path staged = storageService.stageFile(file);

        assertThatThrownBy(() -> storageService.validateStagedFile(staged, "script.sh"))
                .isInstanceOf(MethodArgumentNotValidException.class)
                .hasMessageContaining("File type not allowed");

        assertThat(Files.exists(staged)).isFalse(); // Temporary file deleted
    }

    @Test
    @DisplayName("Reject filename with path traversal characters")
    void validateStagedFile_RejectsPathTraversal() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "safe content".getBytes()
        );
        Path staged = storageService.stageFile(file);

        assertThatThrownBy(() -> storageService.validateStagedFile(staged, "../../etc/passwd"))
                .isInstanceOf(MethodArgumentNotValidException.class)
                .hasMessageContaining("Invalid filename characters");

        assertThat(Files.exists(staged)).isFalse();
    }

    @Test
    @DisplayName("Reject Windows PE executable binary by magic bytes (MZ header)")
    void validateStagedFile_RejectsWindowsPEBinaryMagicBytes() {
        // MZ header: 0x4D, 0x5A followed by dummy binary payload
        byte[] peHeader = new byte[]{0x4D, 0x5A, 0x00, 0x00, 0x12, 0x34};
        MockMultipartFile file = new MockMultipartFile(
                "file", "document.pdf", "application/pdf", peHeader
        );
        Path staged = storageService.stageFile(file);

        assertThatThrownBy(() -> storageService.validateStagedFile(staged, "document.pdf"))
                .isInstanceOf(MethodArgumentNotValidException.class)
                .hasMessageContaining("Executable binary (PE) files are not allowed");

        assertThat(Files.exists(staged)).isFalse();
    }

    @Test
    @DisplayName("Reject Linux ELF binary by magic bytes (\\x7fELF header)")
    void validateStagedFile_RejectsLinuxElfMagicBytes() {
        // ELF header: 0x7F, 0x45, 0x4C, 0x46
        byte[] elfHeader = new byte[]{0x7F, 0x45, 0x4C, 0x46, 0x02, 0x01};
        MockMultipartFile file = new MockMultipartFile(
                "file", "document.pdf", "application/pdf", elfHeader
        );
        Path staged = storageService.stageFile(file);

        assertThatThrownBy(() -> storageService.validateStagedFile(staged, "document.pdf"))
                .isInstanceOf(MethodArgumentNotValidException.class)
                .hasMessageContaining("Executable binary (ELF) files are not allowed");

        assertThat(Files.exists(staged)).isFalse();
    }

    @Test
    @DisplayName("Reject Shell script with shebang (#! header)")
    void validateStagedFile_RejectsShebangMagicBytes() {
        byte[] shebangHeader = "#!/bin/bash\nrm -rf /".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "document.txt", "text/plain", shebangHeader
        );
        Path staged = storageService.stageFile(file);

        assertThatThrownBy(() -> storageService.validateStagedFile(staged, "document.txt"))
                .isInstanceOf(MethodArgumentNotValidException.class)
                .hasMessageContaining("Executable script files are not allowed");

        assertThat(Files.exists(staged)).isFalse();
    }

    @Test
    @DisplayName("Clean abandoned orphaned staging files older than max age")
    void cleanOrphanedStagingFiles_RemovesOldTmpFiles() throws Exception {
        Path stagingDir = storageService.getStagingDestination();
        Files.createDirectories(stagingDir);

        Path oldTmp = stagingDir.resolve("stage_old.tmp");
        Files.writeString(oldTmp, "abandoned staging file");
        // Set last modified time back by 2 hours
        Files.setLastModifiedTime(oldTmp, java.nio.file.attribute.FileTime.from(java.time.Instant.now().minus(Duration.ofHours(2))));

        Path newTmp = stagingDir.resolve("stage_new.tmp");
        Files.writeString(newTmp, "active staging file");

        int cleaned = storageService.cleanOrphanedStagingFiles(Duration.ofHours(1));

        assertThat(cleaned).isEqualTo(1);
        assertThat(Files.exists(oldTmp)).isFalse();
        assertThat(Files.exists(newTmp)).isTrue();
    }
}
