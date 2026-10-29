package com.madn.dropzone.service;

import com.madn.dropzone.entity.StorageAccount;
import com.madn.dropzone.entity.StoredFile;
import com.madn.dropzone.entity.StoredFolder;
import com.madn.dropzone.enums.FileStatus;
import com.madn.dropzone.exception.common.ResourceNotFoundException;
import com.madn.dropzone.exception.file.QuotaExceededException;
import com.madn.dropzone.repository.FileRepository;
import com.madn.dropzone.repository.FolderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FolderArchiveServiceTest {

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private FileRepository fileRepository;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private FolderArchiveService folderArchiveService;

    private UUID userId;
    private StorageAccount storageAccount;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        storageAccount = new StorageAccount();
        storageAccount.setId(userId);
    }

    @Test
    @DisplayName("Throw ResourceNotFoundException when folder does not exist")
    void streamFolderArchive_NotFound_ThrowsException() {
        UUID folderId = UUID.randomUUID();
        when(folderRepository.getStoredFolderByFolderidAndUserId(folderId, userId)).thenReturn(Optional.empty());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        assertThatThrownBy(() -> folderArchiveService.streamFolderArchive(userId, folderId, baos))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Folder not found");
    }

    @Test
    @DisplayName("Throw QuotaExceededException when file count exceeds 5000")
    void streamFolderArchive_FileCountExceeded_ThrowsException() {
        UUID folderId = UUID.randomUUID();
        StoredFolder rootFolder = new StoredFolder();
        rootFolder.setId(folderId);
        rootFolder.setName("LargeFolder");
        rootFolder.setStorageAccount(storageAccount);

        when(folderRepository.getStoredFolderByFolderidAndUserId(folderId, userId)).thenReturn(Optional.of(rootFolder));
        when(fileRepository.countByFolderIdInAndStatus(any(), eq(FileStatus.ACTIVE))).thenReturn(5001L);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        assertThatThrownBy(() -> folderArchiveService.streamFolderArchive(userId, folderId, baos))
                .isInstanceOf(QuotaExceededException.class)
                .hasMessageContaining("Archive exceeds maximum allowable file count");
    }

    @Test
    @DisplayName("Throw QuotaExceededException when uncompressed size exceeds 2 GiB")
    void streamFolderArchive_SizeExceeded_ThrowsException() {
        UUID folderId = UUID.randomUUID();
        StoredFolder rootFolder = new StoredFolder();
        rootFolder.setId(folderId);
        rootFolder.setName("LargeFolder");
        rootFolder.setStorageAccount(storageAccount);

        when(folderRepository.getStoredFolderByFolderidAndUserId(folderId, userId)).thenReturn(Optional.of(rootFolder));
        when(fileRepository.countByFolderIdInAndStatus(any(), eq(FileStatus.ACTIVE))).thenReturn(10L);
        when(fileRepository.sumSizeByFolderIdInAndStatus(any(), eq(FileStatus.ACTIVE)))
                .thenReturn(2L * 1024 * 1024 * 1024 + 1);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        assertThatThrownBy(() -> folderArchiveService.streamFolderArchive(userId, folderId, baos))
                .isInstanceOf(QuotaExceededException.class)
                .hasMessageContaining("Archive exceeds maximum allowable size of 2 GiB");
    }

    @Test
    @DisplayName("Successfully stream ZIP archive preserving folder tree, empty folders, and file contents")
    void streamFolderArchive_Success(@TempDir Path tempDir) throws IOException {
        UUID rootFolderId = UUID.randomUUID();
        StoredFolder rootFolder = new StoredFolder();
        rootFolder.setId(rootFolderId);
        rootFolder.setName("ProjectRoot");
        rootFolder.setStorageAccount(storageAccount);

        UUID subFolderId = UUID.randomUUID();
        StoredFolder subFolder = new StoredFolder();
        subFolder.setId(subFolderId);
        subFolder.setName("EmptySubfolder");
        subFolder.setStorageAccount(storageAccount);
        subFolder.setParentFolder(rootFolder);

        // Physical file on disk
        Path dummyFile = tempDir.resolve("doc.txt");
        Files.writeString(dummyFile, "Hello DropZone Archive!");

        StoredFile storedFile = new StoredFile();
        storedFile.setId(UUID.randomUUID());
        storedFile.setFileName("doc.txt");
        storedFile.setFileSize(Files.size(dummyFile));
        storedFile.setFolder(rootFolder);
        storedFile.setStatus(FileStatus.ACTIVE);

        when(folderRepository.getStoredFolderByFolderidAndUserId(rootFolderId, userId)).thenReturn(Optional.of(rootFolder));
        when(folderRepository.findChildFolders(userId, rootFolderId)).thenReturn(List.of(subFolder));
        when(folderRepository.findChildFolders(userId, subFolderId)).thenReturn(Collections.emptyList());

        when(fileRepository.countByFolderIdInAndStatus(any(), eq(FileStatus.ACTIVE))).thenReturn(1L);
        when(fileRepository.sumSizeByFolderIdInAndStatus(any(), eq(FileStatus.ACTIVE))).thenReturn(Files.size(dummyFile));

        when(fileRepository.getUserFilesInsideFolder(userId, rootFolderId, FileStatus.ACTIVE)).thenReturn(List.of(storedFile));
        when(fileRepository.getUserFilesInsideFolder(userId, subFolderId, FileStatus.ACTIVE)).thenReturn(Collections.emptyList());

        when(storageService.getFilePath(storedFile)).thenReturn(dummyFile);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        folderArchiveService.streamFolderArchive(userId, rootFolderId, baos);

        // Read and verify generated zip bytes
        byte[] zipBytes = baos.toByteArray();
        assertThat(zipBytes).isNotEmpty();

        List<String> entryNames = new ArrayList<>();
        Map<String, String> entryContents = new HashMap<>();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryNames.add(entry.getName());
                if (!entry.isDirectory()) {
                    entryContents.put(entry.getName(), new String(zis.readAllBytes()));
                }
                zis.closeEntry();
            }
        }

        assertThat(entryNames).contains(
                "ProjectRoot/",
                "ProjectRoot/doc.txt",
                "ProjectRoot/EmptySubfolder/"
        );
        assertThat(entryContents.get("ProjectRoot/doc.txt")).isEqualTo("Hello DropZone Archive!");
    }

    @Test
    @DisplayName("Verify Zip-Slip sanitization in sanitizeZipEntryName")
    void sanitizeZipEntryName_SanitizesTraversalAndIllegalCharacters() {
        assertThat(FolderArchiveService.sanitizeZipEntryName("../../secret.txt")).isEqualTo("secret.txt");
        assertThat(FolderArchiveService.sanitizeZipEntryName("..")).isEqualTo("folder");
        assertThat(FolderArchiveService.sanitizeZipEntryName("invalid:file*name?.txt")).isEqualTo("invalid_file_name_.txt");
        assertThat(FolderArchiveService.sanitizeZipEntryName(null)).isEqualTo("unnamed_item");
        assertThat(FolderArchiveService.sanitizeZipEntryName("   ")).isEqualTo("unnamed_item");
    }
}
