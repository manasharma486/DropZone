package com.madn.dropzone.service;

import com.madn.dropzone.dto.StoredFileDto;
import com.madn.dropzone.entity.StorageAccount;
import com.madn.dropzone.entity.StoredFile;
import com.madn.dropzone.entity.StoredFolder;
import com.madn.dropzone.enums.FileStatus;
import com.madn.dropzone.exception.common.DuplicateResourceException;
import com.madn.dropzone.exception.common.ResourceNotFoundException;
import com.madn.dropzone.exception.file.EmptyFileException;
import com.madn.dropzone.exception.file.QuotaExceededException;
import com.madn.dropzone.exception.folder.InvalidParentException;
import com.madn.dropzone.exception.storage.StorageException;
import com.madn.dropzone.exception.validation.MethodArgumentNotValidException;
import com.madn.dropzone.repository.FileRepository;
import com.madn.dropzone.repository.FolderRepository;
import com.madn.dropzone.repository.StorageAccountRepository;
import com.madn.dropzone.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private StorageService storageService;

    @Mock
    private FileRepository fileRepository;

    @Mock
    private StorageAccountRepository storageAccountRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FileService fileService;

    private UUID userId;
    private StorageAccount storageAccount;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        storageAccount = new StorageAccount();
        storageAccount.setId(userId);
        storageAccount.setQuotaBytes(1024L * 1024L * 1024L); // 1 GB
        storageAccount.setUsedBytes(0L);
        storageAccount.setFiles(new ArrayList<>());
        storageAccount.setFolders(new ArrayList<>());
    }

    @Nested
    @DisplayName("uploadFile Tests")
    class UploadFileTests {

        @Test
        @DisplayName("Successfully upload new file (not yet in storage)")
        void uploadFile_NewFile_Success() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "document.pdf", "application/pdf", "dummy content".getBytes()
            );

            when(storageAccountRepository.findByUserId(userId)).thenReturn(Optional.of(storageAccount));
            when(storageService.calculateChecksum(file)).thenReturn("checksum123");
            when(fileRepository.existFileInAccount("checksum123", userId)).thenReturn(Optional.empty());
            when(fileRepository.existFileInStorage("checksum123")).thenReturn(Collections.emptyList());
            when(storageService.saveToStorage(file)).thenReturn("storageKey-abc");

            String result = fileService.uploadFile(List.of(file), userId);

            assertThat(result).isEqualTo("Files saved successfully");
            assertThat(storageAccount.getUsedBytes()).isEqualTo(file.getSize());
            verify(fileRepository).save(argThat(storedFile ->
                    "document.pdf".equals(storedFile.getFileName()) &&
                    "checksum123".equals(storedFile.getChecksum()) &&
                    "storageKey-abc".equals(storedFile.getStorageKey()) &&
                    storedFile.getStatus() == FileStatus.ACTIVE
            ));
            verify(storageAccountRepository).save(storageAccount);
        }

        @Test
        @DisplayName("Successfully upload file with deduplication (already exists in storage for another user)")
        void uploadFile_Deduplication_Success() {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "shared.pdf", "application/pdf", "same content".getBytes()
            );

            StoredFile existingInStorage = new StoredFile();
            existingInStorage.setStorageKey("existing-storage-key");
            existingInStorage.setChecksum("checksum-dup");

            when(storageAccountRepository.findByUserId(userId)).thenReturn(Optional.of(storageAccount));
            when(storageService.calculateChecksum(file)).thenReturn("checksum-dup");
            when(fileRepository.existFileInAccount("checksum-dup", userId)).thenReturn(Optional.empty());
            when(fileRepository.existFileInStorage("checksum-dup")).thenReturn(List.of(existingInStorage));

            String result = fileService.uploadFile(List.of(file), userId);

            assertThat(result).isEqualTo("Files saved successfully");
            verify(storageService, never()).saveToStorage(any());
            verify(fileRepository).save(argThat(saved ->
                    "shared.pdf".equals(saved.getFileName()) &&
                    "existing-storage-key".equals(saved.getStorageKey()) &&
                    "checksum-dup".equals(saved.getChecksum())
            ));
            verify(storageAccountRepository).save(storageAccount);
        }

        @Test
        @DisplayName("Throw ResourceNotFoundException when storage account not found")
        void uploadFile_AccountNotFound_ThrowsException() {
            MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "data".getBytes());
            when(storageAccountRepository.findByUserId(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> fileService.uploadFile(List.of(file), userId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Storage account not found");
        }

        @Test
        @DisplayName("Throw EmptyFileException when file is empty (0 bytes)")
        void uploadFile_EmptyFile_ThrowsException() {
            MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);
            when(storageAccountRepository.findByUserId(userId)).thenReturn(Optional.of(storageAccount));

            assertThatThrownBy(() -> fileService.uploadFile(List.of(emptyFile), userId))
                    .isInstanceOf(EmptyFileException.class)
                    .hasMessageContaining("File cannot be empty");
        }

        @Test
        @DisplayName("Throw QuotaExceededException when total upload exceeds quota")
        void uploadFile_QuotaExceeded_ThrowsException() {
            storageAccount.setQuotaBytes(100L);
            storageAccount.setUsedBytes(50L);

            MockMultipartFile file = new MockMultipartFile("file", "large.bin", "application/octet-stream", new byte[60]);
            when(storageAccountRepository.findByUserId(userId)).thenReturn(Optional.of(storageAccount));

            assertThatThrownBy(() -> fileService.uploadFile(List.of(file), userId))
                    .isInstanceOf(QuotaExceededException.class)
                    .hasMessageContaining("Storage quota exceeded");
        }

        @Test
        @DisplayName("Throw DuplicateResourceException when file already exists in user's drive (ACTIVE)")
        void uploadFile_AlreadyInDrive_ThrowsException() {
            MockMultipartFile file = new MockMultipartFile("file", "duplicate.pdf", "application/pdf", "content".getBytes());

            StoredFile existingFile = new StoredFile();
            existingFile.setStatus(FileStatus.ACTIVE);

            when(storageAccountRepository.findByUserId(userId)).thenReturn(Optional.of(storageAccount));
            when(storageService.calculateChecksum(file)).thenReturn("hash123");
            when(fileRepository.existFileInAccount("hash123", userId)).thenReturn(Optional.of(existingFile));

            assertThatThrownBy(() -> fileService.uploadFile(List.of(file), userId))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("already present in the drive");
        }

        @Test
        @DisplayName("Throw DuplicateResourceException when file already exists in user's bin (DELETED)")
        void uploadFile_AlreadyInBin_ThrowsException() {
            MockMultipartFile file = new MockMultipartFile("file", "duplicate.pdf", "application/pdf", "content".getBytes());

            StoredFile deletedFile = new StoredFile();
            deletedFile.setStatus(FileStatus.DELETED);

            when(storageAccountRepository.findByUserId(userId)).thenReturn(Optional.of(storageAccount));
            when(storageService.calculateChecksum(file)).thenReturn("hash123");
            when(fileRepository.existFileInAccount("hash123", userId)).thenReturn(Optional.of(deletedFile));

            assertThatThrownBy(() -> fileService.uploadFile(List.of(file), userId))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("already present in the bin!");
        }
    }

    @Nested
    @DisplayName("deleteFile Tests")
    class DeleteFileTests {

        @Test
        @DisplayName("Successfully soft delete file (set status to DELETED)")
        void deleteFile_Success() {
            UUID fileId = UUID.randomUUID();
            StoredFile file = new StoredFile();
            file.setId(fileId);
            file.setStatus(FileStatus.ACTIVE);

            when(fileRepository.getStoredFileByIdAndUserId(userId, fileId)).thenReturn(Optional.of(file));

            String result = fileService.deleteFile(fileId, userId);

            assertThat(result).isEqualTo("File Deleated SuccessFully !!");
            assertThat(file.getStatus()).isEqualTo(FileStatus.DELETED);
            verify(fileRepository).save(file);
        }

        @Test
        @DisplayName("Throw ResourceNotFoundException when file to delete does not exist")
        void deleteFile_NotFound_ThrowsException() {
            UUID fileId = UUID.randomUUID();
            when(fileRepository.getStoredFileByIdAndUserId(userId, fileId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> fileService.deleteFile(fileId, userId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("File Does not exist");
        }
    }

    @Nested
    @DisplayName("getDriveFiles Tests")
    class GetDriveFilesTests {

        @Test
        @DisplayName("Successfully get active drive files")
        void getDriveFiles_Success() {
            StoredFile file = new StoredFile();
            file.setId(UUID.randomUUID());
            file.setFileName("notes.txt");
            file.setFileSize(2048L);
            file.setStatus(FileStatus.ACTIVE);
            file.setContentType("text/plain");
            file.setUploadedAt(LocalDateTime.now());

            when(storageAccountRepository.existsById(userId)).thenReturn(true);
            when(fileRepository.getStoredFileFromStatus(userId, FileStatus.ACTIVE, "")).thenReturn(List.of(file));

            List<StoredFileDto> result = fileService.getDriveFiles(userId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getFileName()).isEqualTo("notes.txt");
            assertThat(result.get(0).getFileSize()).isEqualTo("2MB");
        }

        @Test
        @DisplayName("Throw ResourceNotFoundException when user storage account does not exist")
        void getDriveFiles_UserNotFound_ThrowsException() {
            when(storageAccountRepository.existsById(userId)).thenReturn(false);

            assertThatThrownBy(() -> fileService.getDriveFiles(userId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User Does not Exists !!");
        }
    }

    @Nested
    @DisplayName("getDriveFile Tests")
    class GetDriveFileTests {

        @Test
        @DisplayName("Successfully get single file by ID")
        void getDriveFile_Success() {
            UUID fileId = UUID.randomUUID();
            StoredFile file = new StoredFile();
            file.setId(fileId);
            file.setFileName("sample.png");
            file.setFileSize(1024L);
            file.setStatus(FileStatus.ACTIVE);
            file.setContentType("image/png");

            when(fileRepository.getStoredFileByIdAndUserId(userId, fileId)).thenReturn(Optional.of(file));

            StoredFileDto result = fileService.getDriveFile(userId, fileId);

            assertThat(result).isNotNull();
            assertThat(result.getFileId()).isEqualTo(fileId);
            assertThat(result.getFileName()).isEqualTo("sample.png");
        }

        @Test
        @DisplayName("Throw ResourceNotFoundException when single file not found")
        void getDriveFile_NotFound_ThrowsException() {
            UUID fileId = UUID.randomUUID();
            when(fileRepository.getStoredFileByIdAndUserId(userId, fileId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> fileService.getDriveFile(userId, fileId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("File does not exists");
        }
    }

    @Nested
    @DisplayName("updateFileName Tests")
    class UpdateFileNameTests {

        @Test
        @DisplayName("Successfully update file name")
        void updateFileName_Success() {
            UUID fileId = UUID.randomUUID();
            StoredFile file = new StoredFile();
            file.setId(fileId);
            file.setFileName("old.txt");

            when(fileRepository.getStoredFileByIdAndUserId(userId, fileId)).thenReturn(Optional.of(file));

            String result = fileService.updateFileName(userId, fileId, "new.txt");

            assertThat(result).isEqualTo("File Name changed to: new.txt");
            assertThat(file.getFileName()).isEqualTo("new.txt");
            verify(fileRepository).save(file);
        }

        @Test
        @DisplayName("Throw MethodArgumentNotValidException when new name is empty or blank")
        void updateFileName_Blank_ThrowsException() {
            UUID fileId = UUID.randomUUID();

            assertThatThrownBy(() -> fileService.updateFileName(userId, fileId, null))
                    .isInstanceOf(MethodArgumentNotValidException.class)
                    .hasMessageContaining("Filename cannot be empty");

            assertThatThrownBy(() -> fileService.updateFileName(userId, fileId, "   "))
                    .isInstanceOf(MethodArgumentNotValidException.class)
                    .hasMessageContaining("Filename cannot be empty");
        }

        @Test
        @DisplayName("Throw MethodArgumentNotValidException when new name exceeds 255 characters")
        void updateFileName_TooLong_ThrowsException() {
            UUID fileId = UUID.randomUUID();
            String longName = "a".repeat(256);

            assertThatThrownBy(() -> fileService.updateFileName(userId, fileId, longName))
                    .isInstanceOf(MethodArgumentNotValidException.class)
                    .hasMessageContaining("Filename cannot exceed 255 characters");
        }

        @Test
        @DisplayName("Throw MethodArgumentNotValidException when new name is '.' or '..'")
        void updateFileName_DotOrDoubleDot_ThrowsException() {
            UUID fileId = UUID.randomUUID();

            assertThatThrownBy(() -> fileService.updateFileName(userId, fileId, "."))
                    .isInstanceOf(MethodArgumentNotValidException.class)
                    .hasMessageContaining("Invalid filename");

            assertThatThrownBy(() -> fileService.updateFileName(userId, fileId, ".."))
                    .isInstanceOf(MethodArgumentNotValidException.class)
                    .hasMessageContaining("Invalid filename");
        }

        @Test
        @DisplayName("Throw MethodArgumentNotValidException when name contains prohibited characters")
        void updateFileName_ProhibitedCharacters_ThrowsException() {
            UUID fileId = UUID.randomUUID();
            String[] invalid = {"bad/name", "bad\\name", "bad:name", "bad*name", "bad?name", "bad\"name", "bad<name", "bad>name", "bad|name"};

            for (String inv : invalid) {
                assertThatThrownBy(() -> fileService.updateFileName(userId, fileId, inv))
                        .isInstanceOf(MethodArgumentNotValidException.class)
                        .hasMessageContaining("Filename cannot contain these characters");
            }
        }

        @Test
        @DisplayName("Throw MethodArgumentNotValidException when name ends with a dot or space")
        void updateFileName_EndsWithDotOrSpace_ThrowsException() {
            UUID fileId = UUID.randomUUID();

            assertThatThrownBy(() -> fileService.updateFileName(userId, fileId, "name."))
                    .isInstanceOf(MethodArgumentNotValidException.class)
                    .hasMessageContaining("Filename cannot end with a dot or space");
        }

        @Test
        @DisplayName("Throw ResourceNotFoundException when file does not exist")
        void updateFileName_FileNotFound_ThrowsException() {
            UUID fileId = UUID.randomUUID();
            when(fileRepository.getStoredFileByIdAndUserId(userId, fileId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> fileService.updateFileName(userId, fileId, "valid.txt"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("No File Exist");
        }
    }

    @Nested
    @DisplayName("moveFile Tests")
    class MoveFileTests {

        @Test
        @DisplayName("Successfully move file to root (folderId is null)")
        void moveFile_ToRoot_Success() {
            UUID fileId = UUID.randomUUID();
            StoredFile file = new StoredFile();
            file.setId(fileId);
            StoredFolder oldFolder = new StoredFolder();
            file.setFolder(oldFolder);

            when(fileRepository.getStoredFileByIdAndUserId(userId, fileId)).thenReturn(Optional.of(file));

            String result = fileService.moveFile(userId, fileId, null);

            assertThat(result).isEqualTo("File moved successfully!");
            assertThat(file.getFolder()).isNull();
            verify(fileRepository).save(file);
        }

        @Test
        @DisplayName("Successfully move file to destination folder")
        void moveFile_ToFolder_Success() {
            UUID fileId = UUID.randomUUID();
            UUID folderId = UUID.randomUUID();
            StoredFile file = new StoredFile();
            file.setId(fileId);

            StoredFolder folder = new StoredFolder();
            folder.setId(folderId);

            when(fileRepository.getStoredFileByIdAndUserId(userId, fileId)).thenReturn(Optional.of(file));
            when(folderRepository.getStoredFolderByFolderidAndUserId(folderId, userId)).thenReturn(Optional.of(folder));

            String result = fileService.moveFile(userId, fileId, folderId);

            assertThat(result).isEqualTo("File moved successfully!");
            assertThat(file.getFolder()).isEqualTo(folder);
            verify(fileRepository).save(file);
        }

        @Test
        @DisplayName("Throw ResourceNotFoundException when file not found")
        void moveFile_FileNotFound_ThrowsException() {
            UUID fileId = UUID.randomUUID();
            when(fileRepository.getStoredFileByIdAndUserId(userId, fileId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> fileService.moveFile(userId, fileId, null))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("File does not exist or access is denied");
        }

        @Test
        @DisplayName("Throw InvalidParentException when destination folder not found")
        void moveFile_FolderNotFound_ThrowsException() {
            UUID fileId = UUID.randomUUID();
            UUID folderId = UUID.randomUUID();
            StoredFile file = new StoredFile();
            file.setId(fileId);

            when(fileRepository.getStoredFileByIdAndUserId(userId, fileId)).thenReturn(Optional.of(file));
            when(folderRepository.getStoredFolderByFolderidAndUserId(folderId, userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> fileService.moveFile(userId, fileId, folderId))
                    .isInstanceOf(InvalidParentException.class)
                    .hasMessageContaining("Destination folder does not exist");
        }
    }

    @Nested
    @DisplayName("copyFile Tests")
    class CopyFileTests {

        @Test
        @DisplayName("Successfully copy file to destination folder")
        void copyFile_ToFolder_Success() {
            UUID fileId = UUID.randomUUID();
            UUID folderId = UUID.randomUUID();

            StoredFile sourceFile = new StoredFile();
            sourceFile.setId(fileId);
            sourceFile.setFileName("report.docx");
            sourceFile.setFileSize(5000L);
            sourceFile.setStorageKey("key-1");
            sourceFile.setChecksum("hash-1");
            sourceFile.setContentType("application/docx");
            sourceFile.setStorageAccount(storageAccount);

            StoredFolder folder = new StoredFolder();
            folder.setId(folderId);

            when(storageAccountRepository.findByUserId(userId)).thenReturn(Optional.of(storageAccount));
            when(fileRepository.getStoredFileByIdAndUserId(userId, fileId)).thenReturn(Optional.of(sourceFile));
            when(folderRepository.getStoredFolderByFolderidAndUserId(folderId, userId)).thenReturn(Optional.of(folder));

            String result = fileService.copyFile(userId, fileId, folderId);

            assertThat(result).isEqualTo("Copying Successfull !!");
            verify(fileRepository).save(argThat(copied ->
                    "report.docx".equals(copied.getFileName()) &&
                    copied.getFolder() == folder &&
                    "key-1".equals(copied.getStorageKey())
            ));
            verify(storageAccountRepository).save(storageAccount);
        }

        @Test
        @DisplayName("Throw ResourceNotFoundException when user does not exist")
        void copyFile_UserNotFound_ThrowsException() {
            when(storageAccountRepository.findByUserId(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> fileService.copyFile(userId, UUID.randomUUID(), null))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User Does not exist");
        }

        @Test
        @DisplayName("Throw ResourceNotFoundException when source file not found")
        void copyFile_FileNotFound_ThrowsException() {
            UUID fileId = UUID.randomUUID();
            when(storageAccountRepository.findByUserId(userId)).thenReturn(Optional.of(storageAccount));
            when(fileRepository.getStoredFileByIdAndUserId(userId, fileId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> fileService.copyFile(userId, fileId, null))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("File does not exist or access is denied");
        }
    }

    @Nested
    @DisplayName("downloadFile Tests")
    class DownloadFileTests {

        private Path tempFile;

        @BeforeEach
        void createTempFile() throws IOException {
            tempFile = Files.createTempFile("test-download", ".txt");
            Files.writeString(tempFile, "Hello World from Dropzone");
        }

        @AfterEach
        void cleanTempFile() throws IOException {
            Files.deleteIfExists(tempFile);
        }

        @Test
        @DisplayName("Successfully download file with headers and body")
        void downloadFile_Success() {
            UUID fileId = UUID.randomUUID();
            StoredFile file = new StoredFile();
            file.setId(fileId);
            file.setFileName("hello.txt");
            file.setStorageKey("test-storage-key");
            file.setContentType("text/plain");

            when(fileRepository.getStoredFileByIdAndUserId(userId, fileId)).thenReturn(Optional.of(file));
            when(storageService.getFilePath(file)).thenReturn(tempFile);

            ResponseEntity<Resource> response = fileService.downloadFile(userId, fileId);

            assertThat(response).isNotNull();
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getHeaders().getContentType().toString()).isEqualTo("text/plain");
            assertThat(response.getHeaders().getFirst("Content-Disposition")).contains("attachment; filename=\"hello.txt\"");
            assertThat(response.getBody()).isNotNull();
        }

        @Test
        @DisplayName("Throw ResourceNotFoundException when file to download does not exist")
        void downloadFile_FileNotFound_ThrowsException() {
            UUID fileId = UUID.randomUUID();
            when(fileRepository.getStoredFileByIdAndUserId(userId, fileId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> fileService.downloadFile(userId, fileId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("File does not exist or access is denied");
        }
    }

    @Nested
    @DisplayName("searchFiles Tests")
    class SearchFilesTests {

        @Test
        @DisplayName("Successfully search files with active status")
        void searchFiles_Success() {
            StoredFile file = new StoredFile();
            file.setId(UUID.randomUUID());
            file.setFileName("budget2026.xlsx");
            file.setFileSize(4096L);
            file.setStatus(FileStatus.ACTIVE);

            when(userRepository.existsById(userId)).thenReturn(true);
            when(fileRepository.getStoredFilesByFileNameAndAccount("budget", userId, FileStatus.ACTIVE))
                    .thenReturn(List.of(file));

            List<StoredFileDto> results = fileService.searchFiles(userId, "  budget  ", null);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getFileName()).isEqualTo("budget2026.xlsx");
        }

        @Test
        @DisplayName("Throw MethodArgumentNotValidException when searching with TRASHED status")
        void searchFiles_TrashedStatus_ThrowsException() {
            assertThatThrownBy(() -> fileService.searchFiles(userId, "test", FileStatus.TRASHED))
                    .isInstanceOf(MethodArgumentNotValidException.class)
                    .hasMessageContaining("Not Allowed");
        }

        @Test
        @DisplayName("Throw ResourceNotFoundException when user does not exist")
        void searchFiles_UserNotFound_ThrowsException() {
            when(userRepository.existsById(userId)).thenReturn(false);

            assertThatThrownBy(() -> fileService.searchFiles(userId, "query", FileStatus.ACTIVE))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Invalid User ID");
        }

        @Test
        @DisplayName("Throw MethodArgumentNotValidException when search query is empty or blank")
        void searchFiles_EmptyQuery_ThrowsException() {
            when(userRepository.existsById(userId)).thenReturn(true);

            assertThatThrownBy(() -> fileService.searchFiles(userId, "   ", FileStatus.ACTIVE))
                    .isInstanceOf(MethodArgumentNotValidException.class)
                    .hasMessageContaining("Search query cannot be empty");
        }
    }

    @Nested
    @DisplayName("sortDriveFiles Tests")
    class SortDriveFilesTests {

        @Test
        @DisplayName("Successfully sort drive files by name ascending")
        void sortDriveFiles_ByNameAsc_Success() {
            StoredFile file = new StoredFile();
            file.setId(UUID.randomUUID());
            file.setFileName("alpha.txt");
            file.setFileSize(1024L);
            file.setStatus(FileStatus.ACTIVE);

            when(userRepository.existsById(userId)).thenReturn(true);
            when(fileRepository.getStoredFilesByAccount(eq(userId), eq(FileStatus.ACTIVE), any(Sort.class)))
                    .thenReturn(List.of(file));

            List<StoredFileDto> result = fileService.sortDriveFiles(userId, "name", "asc", null);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getFileName()).isEqualTo("alpha.txt");
            verify(fileRepository).getStoredFilesByAccount(eq(userId), eq(FileStatus.ACTIVE), eq(Sort.by("fileName").ascending()));
        }

        @Test
        @DisplayName("Throw MethodArgumentNotValidException when order is invalid")
        void sortDriveFiles_InvalidOrder_ThrowsException() {
            when(userRepository.existsById(userId)).thenReturn(true);

            assertThatThrownBy(() -> fileService.sortDriveFiles(userId, "name", "invalid_order", FileStatus.ACTIVE))
                    .isInstanceOf(MethodArgumentNotValidException.class)
                    .hasMessageContaining("Invalid order type");
        }

        @Test
        @DisplayName("Throw MethodArgumentNotValidException when sort field is invalid")
        void sortDriveFiles_InvalidField_ThrowsException() {
            when(userRepository.existsById(userId)).thenReturn(true);

            assertThatThrownBy(() -> fileService.sortDriveFiles(userId, "unknown_field", "asc", FileStatus.ACTIVE))
                    .isInstanceOf(MethodArgumentNotValidException.class)
                    .hasMessageContaining("Invalid sort field");
        }

        @Test
        @DisplayName("Throw MethodArgumentNotValidException when status is TRASHED")
        void sortDriveFiles_TrashedStatus_ThrowsException() {
            when(userRepository.existsById(userId)).thenReturn(true);

            assertThatThrownBy(() -> fileService.sortDriveFiles(userId, "name", "asc", FileStatus.TRASHED))
                    .isInstanceOf(MethodArgumentNotValidException.class)
                    .hasMessageContaining("Not Allowed");
        }
    }

    @Nested
    @DisplayName("getFilesByFolder Tests")
    class GetFilesByFolderTests {

        @Test
        @DisplayName("Successfully get files inside folder")
        void getFilesByFolder_Success() {
            UUID folderId = UUID.randomUUID();
            StoredFolder folder = new StoredFolder();
            folder.setId(folderId);

            StoredFile file = new StoredFile();
            file.setId(UUID.randomUUID());
            file.setFileName("folder-file.txt");
            file.setFileSize(1024L);
            file.setStatus(FileStatus.ACTIVE);

            when(userRepository.existsById(userId)).thenReturn(true);
            when(folderRepository.getStoredFolderByFolderidAndUserId(folderId, userId)).thenReturn(Optional.of(folder));
            when(fileRepository.getUserFilesInsideFolder(userId, folderId, FileStatus.ACTIVE)).thenReturn(List.of(file));

            List<StoredFileDto> result = fileService.getFilesByFolder(userId, folderId, null);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getFileName()).isEqualTo("folder-file.txt");
        }

        @Test
        @DisplayName("Throw InvalidParentException when folder is invalid or not found")
        void getFilesByFolder_FolderNotFound_ThrowsException() {
            UUID folderId = UUID.randomUUID();
            when(userRepository.existsById(userId)).thenReturn(true);
            when(folderRepository.getStoredFolderByFolderidAndUserId(folderId, userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> fileService.getFilesByFolder(userId, folderId, FileStatus.ACTIVE))
                    .isInstanceOf(InvalidParentException.class)
                    .hasMessageContaining("Invalid Folder provided !!");
        }
    }

    @Nested
    @DisplayName("getFilesByType Tests")
    class GetFilesByTypeTests {

        @Test
        @DisplayName("Successfully get files by content type")
        void getFilesByType_Success() {
            StoredFile file = new StoredFile();
            file.setId(UUID.randomUUID());
            file.setFileName("image.png");
            file.setFileSize(2048L);
            file.setStatus(FileStatus.ACTIVE);
            file.setContentType("image/png");

            when(userRepository.existsById(userId)).thenReturn(true);
            when(fileRepository.getStoredFileFromStatus(userId, FileStatus.ACTIVE, "%image%")).thenReturn(List.of(file));

            List<StoredFileDto> result = fileService.getFilesByType(userId, "image", null);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getFileName()).isEqualTo("image.png");
        }

        @Test
        @DisplayName("Throw MethodArgumentNotValidException when status is TRASHED")
        void getFilesByType_TrashedStatus_ThrowsException() {
            when(userRepository.existsById(userId)).thenReturn(true);

            assertThatThrownBy(() -> fileService.getFilesByType(userId, "image", FileStatus.TRASHED))
                    .isInstanceOf(MethodArgumentNotValidException.class)
                    .hasMessageContaining("Not Allowed");
        }
    }
}
