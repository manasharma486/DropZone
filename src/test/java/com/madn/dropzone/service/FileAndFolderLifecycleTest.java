package com.madn.dropzone.service;

import com.madn.dropzone.dto.BinFileDto;
import com.madn.dropzone.entity.PhysicalStorageObject;
import com.madn.dropzone.entity.StorageAccount;
import com.madn.dropzone.entity.StoredFile;
import com.madn.dropzone.entity.StoredFolder;
import com.madn.dropzone.enums.FileStatus;
import com.madn.dropzone.enums.StorageObjectStatus;
import com.madn.dropzone.exception.common.ResourceNotFoundException;
import com.madn.dropzone.repository.FileRepository;
import com.madn.dropzone.repository.FolderRepository;
import com.madn.dropzone.repository.PhysicalStorageObjectRepository;
import com.madn.dropzone.repository.StorageAccountRepository;
import com.madn.dropzone.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileAndFolderLifecycleTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FileRepository fileRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private StorageAccountRepository storageAccountRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private PhysicalStorageObjectRepository physicalStorageObjectRepository;

    private BinService binService;
    private FolderService folderService;

    private UUID userId;
    private StorageAccount storageAccount;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        storageAccount = new StorageAccount();
        storageAccount.setId(userId);
        storageAccount.setQuotaBytes(10L * 1024 * 1024 * 1024);
        storageAccount.setUsedBytes(5000L);

        binService = new BinService(userRepository, fileRepository, storageAccountRepository, storageService, physicalStorageObjectRepository);
        folderService = new FolderService(folderRepository, fileRepository, storageAccountRepository, userRepository);
    }

    @Test
    @DisplayName("Moving file to bin: marks DELETED, does NOT decrement physical ref count or delete physical file")
    void moveFileToBin_PreservesStorageReference() {
        UUID fileId = UUID.randomUUID();
        StoredFile file = new StoredFile();
        file.setId(fileId);
        file.setStatus(FileStatus.ACTIVE);
        file.setChecksum("sha256-abc");
        file.setStorageKey("storage-key-1");

        FileService fileService = new FileService(storageService, fileRepository, storageAccountRepository, folderRepository, userRepository);

        when(fileRepository.getStoredFileByIdAndUserId(userId, fileId)).thenReturn(Optional.of(file));

        String result = fileService.deleteFile(fileId, userId);

        assertThat(result).contains("SuccessFully");
        assertThat(file.getStatus()).isEqualTo(FileStatus.DELETED);
        verify(fileRepository).save(file);
        // Physical storage operations must NOT be called on move to bin
        verify(storageService, never()).deleteFromStorage(any());
        verify(physicalStorageObjectRepository, never()).decrementReferenceCount(any());
    }

    @Test
    @DisplayName("Restoring file from bin: restores status to ACTIVE without modifying physical reference count")
    void restoreFileFromBin_RestoresActiveStatus() {
        UUID fileId = UUID.randomUUID();
        StoredFile file = new StoredFile();
        file.setId(fileId);
        file.setStatus(FileStatus.DELETED);
        file.setChecksum("sha256-abc");
        file.setStorageKey("storage-key-1");

        when(fileRepository.getStoredFileByIdAndUserId(userId, fileId)).thenReturn(Optional.of(file));
        when(storageService.getFilePath(file)).thenReturn(Path.of("/dummy/storage/storage-key-1"));

        BinFileDto dto = new BinFileDto(List.of(fileId));
        String result = binService.restoreFiles(userId, dto);

        assertThat(result).isEqualTo("Selected files restored successfully!");
        assertThat(file.getStatus()).isEqualTo(FileStatus.ACTIVE);
        verify(fileRepository).saveAll(List.of(file));
        // Must NOT decrement or increment physical storage object reference count
        verify(physicalStorageObjectRepository, never()).decrementReferenceCount(any());
        verify(physicalStorageObjectRepository, never()).incrementReferenceCount(any());
    }

    @Test
    @DisplayName("Restoring file fails if underlying physical storage file is no longer valid")
    void restoreFileFromBin_FailsIfPhysicalStorageInvalid() {
        UUID fileId = UUID.randomUUID();
        StoredFile file = new StoredFile();
        file.setId(fileId);
        file.setStatus(FileStatus.DELETED);
        file.setChecksum("sha256-missing");

        when(fileRepository.getStoredFileByIdAndUserId(userId, fileId)).thenReturn(Optional.of(file));
        when(storageService.getFilePath(file)).thenThrow(new com.madn.dropzone.exception.storage.StorageFileNotFoundException("File missing"));

        BinFileDto dto = new BinFileDto(List.of(fileId));

        assertThatThrownBy(() -> binService.restoreFiles(userId, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("underlying storage object is no longer valid");

        assertThat(file.getStatus()).isEqualTo(FileStatus.DELETED);
        verify(fileRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Permanent deletion: removes metadata, decrements physical ref count, does NOT delete physical file immediately")
    void permanentDelete_DecrementsReferenceAndDelegatesPhysicalDeletionToGC() {
        UUID fileId = UUID.randomUUID();
        StoredFile file = new StoredFile();
        file.setId(fileId);
        file.setFileSize(2000L);
        file.setStatus(FileStatus.DELETED);
        file.setChecksum("sha256-file");
        file.setStorageKey("key-1");

        when(storageAccountRepository.findByUserId(userId)).thenReturn(Optional.of(storageAccount));
        when(fileRepository.getStoredFileByIdAndUserId(userId, fileId)).thenReturn(Optional.of(file));

        BinFileDto dto = new BinFileDto(List.of(fileId));
        String result = binService.deleteFiles(userId, dto);

        assertThat(result).isEqualTo("Files deleted successfully!");
        assertThat(storageAccount.getUsedBytes()).isEqualTo(3000L);

        // Metadata deleted
        verify(fileRepository).delete(file);
        // Physical reference count decremented
        verify(physicalStorageObjectRepository).decrementReferenceCount("sha256-file");
        // Physical file deletion NEVER called in BinService (dedicated GC handles it)
        verify(storageService, never()).deleteFromStorage(any());
    }

    @Test
    @DisplayName("Move folder to bin: marks folder and nested files/folders as DELETED, preserves hierarchy")
    void deleteFolder_MovesHierarchyToBin() {
        UUID folderId = UUID.randomUUID();
        StoredFolder folder = new StoredFolder();
        folder.setId(folderId);
        folder.setName("Projects");
        folder.setStatus(FileStatus.ACTIVE);

        StoredFile nestedFile = new StoredFile();
        nestedFile.setId(UUID.randomUUID());
        nestedFile.setStatus(FileStatus.ACTIVE);
        nestedFile.setFolder(folder);

        when(folderRepository.getStoredFolderByFolderidAndUserId(folderId, userId)).thenReturn(Optional.of(folder));
        when(fileRepository.getUserFilesInsideFolder(userId, folderId, FileStatus.ACTIVE)).thenReturn(List.of(nestedFile));

        String result = folderService.deleteFolder(userId, folderId);

        assertThat(result).isEqualTo("Folder deleted successfully");
        assertThat(folder.getStatus()).isEqualTo(FileStatus.DELETED);
        assertThat(nestedFile.getStatus()).isEqualTo(FileStatus.DELETED);
        // Folder association preserved
        assertThat(nestedFile.getFolder()).isEqualTo(folder);

        verify(folderRepository).save(folder);
        verify(fileRepository).save(nestedFile);
        // Folder entity NOT deleted from DB
        verify(folderRepository, never()).delete(any());
    }
}
