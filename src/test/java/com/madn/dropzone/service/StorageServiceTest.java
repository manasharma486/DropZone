package com.madn.dropzone.service;

import com.madn.dropzone.entity.StoredFile;
import com.madn.dropzone.exception.file.EmptyFileException;
import com.madn.dropzone.exception.storage.StorageFileNotFoundException;
import com.madn.dropzone.exception.validation.MethodArgumentNotValidException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageServiceTest {

    private StorageService storageService;

    @TempDir
    Path tempStorageDir;

    @BeforeEach
    void setUp() {
        storageService = new StorageService();
        ReflectionTestUtils.setField(storageService, "storageDestination", tempStorageDir);
    }

    @Test
    @DisplayName("saveToStorage throws EmptyFileException when file is empty")
    void saveToStorage_EmptyFile_ThrowsEmptyFileException() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "test.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> storageService.saveToStorage(emptyFile))
                .isInstanceOf(EmptyFileException.class)
                .hasMessageContaining("File cannot be empty");
    }

    @Test
    @DisplayName("saveToStorage throws MethodArgumentNotValidException when filename is blank")
    void saveToStorage_BlankFilename_ThrowsMethodArgumentNotValidException() {
        MockMultipartFile file = new MockMultipartFile("file", "   ", "text/plain", "content".getBytes());

        assertThatThrownBy(() -> storageService.saveToStorage(file))
                .isInstanceOf(MethodArgumentNotValidException.class)
                .hasMessageContaining("Invalid filename");
    }

    @Test
    @DisplayName("saveToStorage successfully stores file and returns storageKey")
    void saveToStorage_Success() {
        MockMultipartFile file = new MockMultipartFile("file", "document.pdf", "application/pdf", "sample data".getBytes());

        String storageKey = storageService.saveToStorage(file);

        assertThat(storageKey).isNotNull().isNotBlank();
        Path writtenFile = tempStorageDir.resolve(storageKey);
        assertThat(Files.exists(writtenFile)).isTrue();
    }

    @Test
    @DisplayName("calculateChecksum throws EmptyFileException when file is empty")
    void calculateChecksum_EmptyFile_ThrowsEmptyFileException() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> storageService.calculateChecksum(emptyFile))
                .isInstanceOf(EmptyFileException.class)
                .hasMessageContaining("Cannot calculate checksum for an empty file");
    }

    @Test
    @DisplayName("calculateChecksum successfully computes SHA-256 hex string")
    void calculateChecksum_Success() {
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "hello".getBytes());

        String checksum = storageService.calculateChecksum(file);

        assertThat(checksum).isNotNull().hasSize(64);
        // SHA-256 of "hello"
        assertThat(checksum).isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }

    @Test
    @DisplayName("deleteFromStorage removes existing file")
    void deleteFromStorage_Success() throws IOException {
        String key = UUID.randomUUID().toString();
        Path filePath = tempStorageDir.resolve(key);
        Files.writeString(filePath, "temp content");

        assertThat(Files.exists(filePath)).isTrue();

        storageService.deleteFromStorage(key);

        assertThat(Files.exists(filePath)).isFalse();
    }

    @Test
    @DisplayName("getFilePath throws StorageFileNotFoundException when physical file does not exist")
    void getFilePath_MissingFile_ThrowsStorageFileNotFoundException() {
        StoredFile storedFile = new StoredFile();
        storedFile.setStorageKey(UUID.randomUUID().toString());

        assertThatThrownBy(() -> storageService.getFilePath(storedFile))
                .isInstanceOf(StorageFileNotFoundException.class)
                .hasMessageContaining("Physical file does not exist or is unreadable");
    }

    @Test
    @DisplayName("getFilePath returns path when physical file exists")
    void getFilePath_Success() throws IOException {
        String key = UUID.randomUUID().toString();
        Path filePath = tempStorageDir.resolve(key);
        Files.writeString(filePath, "data");

        StoredFile storedFile = new StoredFile();
        storedFile.setStorageKey(key);

        Path resolvedPath = storageService.getFilePath(storedFile);

        assertThat(resolvedPath).isEqualTo(filePath.normalize());
    }
}
