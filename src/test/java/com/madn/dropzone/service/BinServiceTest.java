package com.madn.dropzone.service;

import com.madn.dropzone.dto.BinFileDto;
import com.madn.dropzone.dto.StoredFileDto;
import com.madn.dropzone.entity.StorageAccount;
import com.madn.dropzone.entity.StoredFile;
import com.madn.dropzone.enums.FileStatus;
import com.madn.dropzone.exception.common.DataIntegrityViolationException;
import com.madn.dropzone.exception.common.ResourceNotFoundException;
import com.madn.dropzone.exception.validation.MethodArgumentNotValidException;
import com.madn.dropzone.repository.FileRepository;
import com.madn.dropzone.repository.StorageAccountRepository;
import com.madn.dropzone.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BinServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FileRepository fileRepository;

    @Mock
    private StorageAccountRepository storageAccountRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private com.madn.dropzone.repository.PhysicalStorageObjectRepository physicalStorageObjectRepository;

    @InjectMocks
    private BinService binService;

    private UUID userId;
    private StorageAccount storageAccount;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        storageAccount = new StorageAccount();
        storageAccount.setId(userId);
        storageAccount.setQuotaBytes(1024L * 1024L * 1024L); // 1 GB
        storageAccount.setUsedBytes(5000L);
        storageAccount.setFiles(new ArrayList<>());
    }

    @Nested
    @DisplayName("getFilesInBin Tests")
    class GetFilesInBinTests {

        @Test
        @DisplayName("Successfully get files in bin filtered by type")
        void getFilesInBin_Success() {
            StoredFile file1 = new StoredFile();
            file1.setId(UUID.randomUUID());
            file1.setFileName("document.pdf");
            file1.setFileSize(2048L);
            file1.setStatus(FileStatus.DELETED);
            file1.setContentType("application/pdf");
            file1.setUploadedAt(LocalDateTime.now());

            when(userRepository.existsById(userId)).thenReturn(true);
            when(fileRepository.getStoredFileFromStatus(userId, FileStatus.DELETED, "%pdf%"))
                    .thenReturn(List.of(file1));

            List<StoredFileDto> result = binService.getFilesInBin(userId, "pdf");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getFileName()).isEqualTo("document.pdf");
            assertThat(result.get(0).getStatus()).isEqualTo(FileStatus.DELETED);
        }

        @Test
        @DisplayName("Throw ResourceNotFoundException when user does not exist")
        void getFilesInBin_UserNotFound_ThrowsException() {
            when(userRepository.existsById(userId)).thenReturn(false);

            assertThatThrownBy(() -> binService.getFilesInBin(userId, "pdf"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User Does not Exists !!");
        }
    }

    @Nested
    @DisplayName("restoreFiles Tests")
    class RestoreFilesTests {

        @Test
        @DisplayName("Successfully restore selected deleted files to ACTIVE")
        void restoreFiles_Success() {
            UUID fileId1 = UUID.randomUUID();
            UUID fileId2 = UUID.randomUUID();

            StoredFile file1 = new StoredFile();
            file1.setId(fileId1);
            file1.setStatus(FileStatus.DELETED);

            StoredFile file2 = new StoredFile();
            file2.setId(fileId2);
            file2.setStatus(FileStatus.DELETED);

            when(fileRepository.getStoredFileByIdAndUserId(userId, fileId1)).thenReturn(Optional.of(file1));
            when(fileRepository.getStoredFileByIdAndUserId(userId, fileId2)).thenReturn(Optional.of(file2));

            BinFileDto binFileDto = new BinFileDto(List.of(fileId1, fileId2));

            String result = binService.restoreFiles(userId, binFileDto);

            assertThat(result).isEqualTo("Selected files restored successfully!");
            assertThat(file1.getStatus()).isEqualTo(FileStatus.ACTIVE);
            assertThat(file2.getStatus()).isEqualTo(FileStatus.ACTIVE);
            verify(fileRepository).saveAll(List.of(file1, file2));
        }

        @Test
        @DisplayName("Return 'Nothing to restore' when file list is null or empty")
        void restoreFiles_EmptyList_ReturnsNothingToRestore() {
            BinFileDto nullDto = new BinFileDto(null);
            assertThat(binService.restoreFiles(userId, nullDto)).isEqualTo("Nothing to restore");

            BinFileDto emptyDto = new BinFileDto(Collections.emptyList());
            assertThat(binService.restoreFiles(userId, emptyDto)).isEqualTo("Nothing to restore");

            verify(fileRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("Deduplicate file IDs if duplicate IDs provided in request")
        void restoreFiles_DeduplicatesInput() {
            UUID fileId = UUID.randomUUID();
            StoredFile file = new StoredFile();
            file.setId(fileId);
            file.setStatus(FileStatus.DELETED);

            when(fileRepository.getStoredFileByIdAndUserId(userId, fileId)).thenReturn(Optional.of(file));

            BinFileDto dto = new BinFileDto(List.of(fileId, fileId, fileId));

            String result = binService.restoreFiles(userId, dto);

            assertThat(result).isEqualTo("Selected files restored successfully!");
            verify(fileRepository, times(1)).getStoredFileByIdAndUserId(userId, fileId);
            verify(fileRepository).saveAll(List.of(file));
        }

        @Test
        @DisplayName("Throw ResourceNotFoundException when file to restore is not found")
        void restoreFiles_NotFound_ThrowsException() {
            UUID fileId = UUID.randomUUID();
            when(fileRepository.getStoredFileByIdAndUserId(userId, fileId)).thenReturn(Optional.empty());

            BinFileDto dto = new BinFileDto(List.of(fileId));

            assertThatThrownBy(() -> binService.restoreFiles(userId, dto))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("File not found: " + fileId);
        }

        @Test
        @DisplayName("Throw MethodArgumentNotValidException when file to restore is not in DELETED status")
        void restoreFiles_NotDeletedStatus_ThrowsException() {
            UUID fileId = UUID.randomUUID();
            StoredFile file = new StoredFile();
            file.setId(fileId);
            file.setStatus(FileStatus.ACTIVE); // Not DELETED

            when(fileRepository.getStoredFileByIdAndUserId(userId, fileId)).thenReturn(Optional.of(file));

            BinFileDto dto = new BinFileDto(List.of(fileId));

            assertThatThrownBy(() -> binService.restoreFiles(userId, dto))
                    .isInstanceOf(MethodArgumentNotValidException.class)
                    .hasMessageContaining("Only deleted files can be restored");
        }
    }

    @Nested
    @DisplayName("restoreAllFiles Tests")
    class RestoreAllFilesTests {

        @Test
        @DisplayName("Successfully restore all deleted files")
        void restoreAllFiles_Success() {
            StoredFile file1 = new StoredFile();
            file1.setId(UUID.randomUUID());
            file1.setStatus(FileStatus.DELETED);

            StoredFile file2 = new StoredFile();
            file2.setId(UUID.randomUUID());
            file2.setStatus(FileStatus.DELETED);

            when(fileRepository.getStoredFileFromStatus(userId, FileStatus.DELETED, ""))
                    .thenReturn(List.of(file1, file2));

            String result = binService.restoreAllFiles(userId);

            assertThat(result).isEqualTo("All files have been restored successfully!");
            assertThat(file1.getStatus()).isEqualTo(FileStatus.ACTIVE);
            assertThat(file2.getStatus()).isEqualTo(FileStatus.ACTIVE);
            verify(fileRepository).saveAll(List.of(file1, file2));
        }

        @Test
        @DisplayName("Return 'Nothing to restore' when bin is empty")
        void restoreAllFiles_EmptyBin_ReturnsNothing() {
            when(fileRepository.getStoredFileFromStatus(userId, FileStatus.DELETED, ""))
                    .thenReturn(Collections.emptyList());

            String result = binService.restoreAllFiles(userId);

            assertThat(result).isEqualTo("Nothing to restore");
            verify(fileRepository, never()).saveAll(any());
        }
    }

    @Nested
    @DisplayName("deleteFiles Tests")
    class DeleteFilesTests {

        @Test
        @DisplayName("Successfully permanently delete files and remove from physical storage when no remaining references")
        void deleteFiles_NoRemainingReferences_DeletesFromStorage() {
            UUID fileId = UUID.randomUUID();
            StoredFile file = new StoredFile();
            file.setId(fileId);
            file.setFileSize(2000L);
            file.setStatus(FileStatus.DELETED);
            file.setChecksum("checksum-abc");
            file.setStorageKey("storage-key-1");

            when(storageAccountRepository.findByUserId(userId)).thenReturn(Optional.of(storageAccount));
            when(fileRepository.getStoredFileByIdAndUserId(userId, fileId)).thenReturn(Optional.of(file));
            BinFileDto dto = new BinFileDto(List.of(fileId));

            String result = binService.deleteFiles(userId, dto);

            assertThat(result).isEqualTo("Files deleted successfully!");
            assertThat(storageAccount.getUsedBytes()).isEqualTo(3000L); // 5000 - 2000

            verify(physicalStorageObjectRepository).decrementReferenceCount("checksum-abc");
            verify(storageService, never()).deleteFromStorage(any());
            verify(fileRepository).delete(file);
            verify(storageAccountRepository).save(storageAccount);
        }

        @Test
        @DisplayName("Successfully delete files without removing from physical storage when other references exist")
        void deleteFiles_RemainingReferencesExist_KeepsInStorage() {
            UUID fileId = UUID.randomUUID();
            StoredFile file = new StoredFile();
            file.setId(fileId);
            file.setFileSize(1000L);
            file.setStatus(FileStatus.DELETED);
            file.setChecksum("checksum-shared");
            file.setStorageKey("shared-storage-key");

            when(storageAccountRepository.findByUserId(userId)).thenReturn(Optional.of(storageAccount));
            when(fileRepository.getStoredFileByIdAndUserId(userId, fileId)).thenReturn(Optional.of(file));
            BinFileDto dto = new BinFileDto(List.of(fileId));

            String result = binService.deleteFiles(userId, dto);

            assertThat(result).isEqualTo("Files deleted successfully!");
            assertThat(storageAccount.getUsedBytes()).isEqualTo(4000L); // 5000 - 1000

            verify(physicalStorageObjectRepository).decrementReferenceCount("checksum-shared");
            verify(storageService, never()).deleteFromStorage(any());
            verify(fileRepository).delete(file);
            verify(storageAccountRepository).save(storageAccount);
        }

        @Test
        @DisplayName("Throw ResourceNotFoundException when user account does not exist")
        void deleteFiles_AccountNotFound_ThrowsException() {
            when(storageAccountRepository.findByUserId(userId)).thenReturn(Optional.empty());

            BinFileDto dto = new BinFileDto(List.of(UUID.randomUUID()));

            assertThatThrownBy(() -> binService.deleteFiles(userId, dto))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User does not exist");
        }

        @Test
        @DisplayName("Throw MethodArgumentNotValidException when file IDs list is null or empty")
        void deleteFiles_EmptyFileIds_ThrowsException() {
            when(storageAccountRepository.findByUserId(userId)).thenReturn(Optional.of(storageAccount));

            assertThatThrownBy(() -> binService.deleteFiles(userId, new BinFileDto(null)))
                    .isInstanceOf(MethodArgumentNotValidException.class)
                    .hasMessageContaining("No file IDs provided");

            assertThatThrownBy(() -> binService.deleteFiles(userId, new BinFileDto(Collections.emptyList())))
                    .isInstanceOf(MethodArgumentNotValidException.class)
                    .hasMessageContaining("No file IDs provided");
        }

        @Test
        @DisplayName("Throw ResourceNotFoundException when file is not found")
        void deleteFiles_FileNotFound_ThrowsException() {
            UUID fileId = UUID.randomUUID();
            when(storageAccountRepository.findByUserId(userId)).thenReturn(Optional.of(storageAccount));
            when(fileRepository.getStoredFileByIdAndUserId(userId, fileId)).thenReturn(Optional.empty());

            BinFileDto dto = new BinFileDto(List.of(fileId));

            assertThatThrownBy(() -> binService.deleteFiles(userId, dto))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("File not found: " + fileId);
        }

        @Test
        @DisplayName("Throw MethodArgumentNotValidException when file is not in DELETED status")
        void deleteFiles_NotDeletedStatus_ThrowsException() {
            UUID fileId = UUID.randomUUID();
            StoredFile file = new StoredFile();
            file.setId(fileId);
            file.setStatus(FileStatus.ACTIVE);

            when(storageAccountRepository.findByUserId(userId)).thenReturn(Optional.of(storageAccount));
            when(fileRepository.getStoredFileByIdAndUserId(userId, fileId)).thenReturn(Optional.of(file));

            BinFileDto dto = new BinFileDto(List.of(fileId));

            assertThatThrownBy(() -> binService.deleteFiles(userId, dto))
                    .isInstanceOf(MethodArgumentNotValidException.class)
                    .hasMessageContaining("Only deleted files can be permanently removed");
        }

        @Test
        @DisplayName("Safely clamp storage usage to zero when total bytes to remove exceeds used bytes")
        void deleteFiles_NegativeUsage_ClampsToZero() {
            UUID fileId = UUID.randomUUID();
            StoredFile file = new StoredFile();
            file.setId(fileId);
            file.setFileSize(10000L); // larger than account usedBytes (5000L)
            file.setStatus(FileStatus.DELETED);

            when(storageAccountRepository.findByUserId(userId)).thenReturn(Optional.of(storageAccount));
            when(fileRepository.getStoredFileByIdAndUserId(userId, fileId)).thenReturn(Optional.of(file));

            BinFileDto dto = new BinFileDto(List.of(fileId));

            String result = binService.deleteFiles(userId, dto);

            assertThat(result).isEqualTo("Files deleted successfully!");
            assertThat(storageAccount.getUsedBytes()).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("deleteAllFiles Tests")
    class DeleteAllFilesTests {

        @Test
        @DisplayName("Successfully delete all files from bin")
        void deleteAllFiles_Success() {
            StoredFile file1 = new StoredFile();
            file1.setId(UUID.randomUUID());
            file1.setFileSize(2000L);
            file1.setStatus(FileStatus.DELETED);
            file1.setChecksum("checksum-1");
            file1.setStorageKey("key-1");

            when(storageAccountRepository.findByUserId(userId)).thenReturn(Optional.of(storageAccount));
            when(fileRepository.getStoredFileFromStatus(userId, FileStatus.DELETED, ""))
                    .thenReturn(List.of(file1));

            String result = binService.deleteAllFiles(userId);

            assertThat(result).isEqualTo("All files deleted successfully!");
            assertThat(storageAccount.getUsedBytes()).isEqualTo(3000L);

            verify(physicalStorageObjectRepository).decrementReferenceCount("checksum-1");
            verify(storageService, never()).deleteFromStorage(any());
            verify(fileRepository).delete(file1);
            verify(storageAccountRepository).save(storageAccount);
        }

        @Test
        @DisplayName("Return 'Bin is already empty!' when bin has no deleted files")
        void deleteAllFiles_EmptyBin_ReturnsAlreadyEmpty() {
            when(storageAccountRepository.findByUserId(userId)).thenReturn(Optional.of(storageAccount));
            when(fileRepository.getStoredFileFromStatus(userId, FileStatus.DELETED, ""))
                    .thenReturn(Collections.emptyList());

            String result = binService.deleteAllFiles(userId);

            assertThat(result).isEqualTo("Bin is already empty!");
            verify(fileRepository, never()).saveAllAndFlush(any());
        }

        @Test
        @DisplayName("Throw ResourceNotFoundException when user account does not exist")
        void deleteAllFiles_AccountNotFound_ThrowsException() {
            when(storageAccountRepository.findByUserId(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> binService.deleteAllFiles(userId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User does not exist");
        }
    }
}
