package com.madn.dropzone.service;

import com.madn.dropzone.dto.BreadcrumbItemDto;
import com.madn.dropzone.dto.FileFolderDto;
import com.madn.dropzone.dto.StoredFolderDto;
import com.madn.dropzone.entity.StorageAccount;
import com.madn.dropzone.entity.StoredFile;
import com.madn.dropzone.entity.StoredFolder;
import com.madn.dropzone.enums.FileStatus;
import com.madn.dropzone.exception.common.DuplicateResourceException;
import com.madn.dropzone.exception.common.ResourceNotFoundException;
import com.madn.dropzone.exception.file.QuotaExceededException;
import com.madn.dropzone.exception.folder.HierarchyCycleException;
import com.madn.dropzone.exception.folder.InvalidParentException;
import com.madn.dropzone.exception.validation.MethodArgumentNotValidException;
import com.madn.dropzone.repository.FileRepository;
import com.madn.dropzone.repository.FolderRepository;
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

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FolderServiceTest {

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private FileRepository fileRepository;

    @Mock
    private StorageAccountRepository storageAccountRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FolderService folderService;

    private UUID userId;
    private StorageAccount storageAccount;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        storageAccount = new StorageAccount();
        storageAccount.setId(userId);
        storageAccount.setQuotaBytes(1024L * 1024L * 1024L); // 1 GB
        storageAccount.setUsedBytes(100L * 1024L); // 100 KB
        storageAccount.setFolders(new ArrayList<>());
        storageAccount.setFiles(new ArrayList<>());
    }

    @Nested
    @DisplayName("createFolder Tests")
    class CreateFolderTests {

        @Test
        @DisplayName("Successfully create folder at root level")
        void createFolder_AtRoot_Success() {
            StoredFolderDto dto = new StoredFolderDto();
            dto.setFolderName("Documents");

            StoredFolder savedFolder = new StoredFolder();
            UUID savedId = UUID.randomUUID();
            savedFolder.setId(savedId);
            savedFolder.setName("Documents");
            savedFolder.setStorageAccount(storageAccount);

            when(storageAccountRepository.findByUserId(userId)).thenReturn(Optional.of(storageAccount));
            when(folderRepository.save(any(StoredFolder.class))).thenReturn(savedFolder);

            String result = folderService.createFolder(userId, dto);

            assertThat(result).contains("Folder created successfully: " + savedId);
            verify(folderRepository).save(argThat(folder ->
                    "Documents".equals(folder.getName()) &&
                    folder.getParentFolder() == null &&
                    folder.getStorageAccount() == storageAccount
            ));
        }

        @Test
        @DisplayName("Successfully create folder under parent folder")
        void createFolder_UnderParent_Success() {
            UUID parentId = UUID.randomUUID();
            StoredFolder parent = new StoredFolder();
            parent.setId(parentId);
            parent.setName("Parent");
            parent.setStorageAccount(storageAccount);
            parent.setChildFolders(new ArrayList<>());

            StoredFolderDto dto = new StoredFolderDto();
            dto.setFolderName("ChildFolder");
            dto.setParentFolder(parentId);

            StoredFolder savedFolder = new StoredFolder();
            UUID savedId = UUID.randomUUID();
            savedFolder.setId(savedId);
            savedFolder.setName("ChildFolder");
            savedFolder.setParentFolder(parent);

            when(storageAccountRepository.findByUserId(userId)).thenReturn(Optional.of(storageAccount));
            when(folderRepository.getStoredFolderByFolderidAndUserId(parentId, userId)).thenReturn(Optional.of(parent));
            when(folderRepository.save(any(StoredFolder.class))).thenReturn(savedFolder);

            String result = folderService.createFolder(userId, dto);

            assertThat(result).contains("Folder created successfully: " + savedId);
            verify(folderRepository).save(argThat(folder ->
                    "ChildFolder".equals(folder.getName()) &&
                    parent.equals(folder.getParentFolder())
            ));
        }

        @Test
        @DisplayName("Throw ResourceNotFoundException when storage account not found")
        void createFolder_AccountNotFound_ThrowsException() {
            StoredFolderDto dto = new StoredFolderDto();
            dto.setFolderName("Documents");

            when(storageAccountRepository.findByUserId(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> folderService.createFolder(userId, dto))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Storage account not found");
        }

        @Test
        @DisplayName("Throw MethodArgumentNotValidException when folder name is null or blank")
        void createFolder_NullOrBlankName_ThrowsException() {
            when(storageAccountRepository.findByUserId(userId)).thenReturn(Optional.of(storageAccount));

            StoredFolderDto nullDto = null;
            assertThatThrownBy(() -> folderService.createFolder(userId, nullDto))
                    .isInstanceOf(MethodArgumentNotValidException.class)
                    .hasMessageContaining("Folder name cannot be empty");

            StoredFolderDto emptyDto = new StoredFolderDto();
            emptyDto.setFolderName("   ");
            assertThatThrownBy(() -> folderService.createFolder(userId, emptyDto))
                    .isInstanceOf(MethodArgumentNotValidException.class)
                    .hasMessageContaining("Folder name cannot be empty");
        }

        @Test
        @DisplayName("Throw MethodArgumentNotValidException when folder name contains prohibited characters")
        void createFolder_InvalidNameCharacters_ThrowsException() {
            when(storageAccountRepository.findByUserId(userId)).thenReturn(Optional.of(storageAccount));

            String[] invalidNames = {"fold/er", "fold\\er", "fold:er", "fold*er", "fold?er", "fold\"er", "fold<er", "fold>er", "fold|er"};

            for (String invalidName : invalidNames) {
                StoredFolderDto dto = new StoredFolderDto();
                dto.setFolderName(invalidName);

                assertThatThrownBy(() -> folderService.createFolder(userId, dto))
                        .isInstanceOf(MethodArgumentNotValidException.class)
                        .hasMessageContaining("prohibited characters");
            }
        }

        @Test
        @DisplayName("Throw MethodArgumentNotValidException when folder name exceeds 255 characters")
        void createFolder_NameExceeds255Chars_ThrowsException() {
            when(storageAccountRepository.findByUserId(userId)).thenReturn(Optional.of(storageAccount));

            StoredFolderDto dto = new StoredFolderDto();
            dto.setFolderName("a".repeat(256));

            assertThatThrownBy(() -> folderService.createFolder(userId, dto))
                    .isInstanceOf(MethodArgumentNotValidException.class)
                    .hasMessageContaining("Folder name cannot exceed 255 characters");
        }

        @Test
        @DisplayName("Throw InvalidParentException when parent folder does not exist or access denied")
        void createFolder_ParentNotFound_ThrowsException() {
            UUID parentId = UUID.randomUUID();
            StoredFolderDto dto = new StoredFolderDto();
            dto.setFolderName("Child");
            dto.setParentFolder(parentId);

            when(storageAccountRepository.findByUserId(userId)).thenReturn(Optional.of(storageAccount));
            when(folderRepository.getStoredFolderByFolderidAndUserId(parentId, userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> folderService.createFolder(userId, dto))
                    .isInstanceOf(InvalidParentException.class)
                    .hasMessageContaining("Parent folder does not exist or access is denied");
        }

        @Test
        @DisplayName("Throw DuplicateResourceException when sibling folder with same name exists at root")
        void createFolder_DuplicateSiblingAtRoot_ThrowsException() {
            StoredFolder existingFolder = new StoredFolder();
            existingFolder.setName("Documents");
            existingFolder.setParentFolder(null);
            storageAccount.getFolders().add(existingFolder);

            StoredFolderDto dto = new StoredFolderDto();
            dto.setFolderName("documents"); // case-insensitive check

            when(storageAccountRepository.findByUserId(userId)).thenReturn(Optional.of(storageAccount));

            assertThatThrownBy(() -> folderService.createFolder(userId, dto))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("already exists in this location");
        }

        @Test
        @DisplayName("Throw DuplicateResourceException when sibling folder with same name exists under parent")
        void createFolder_DuplicateSiblingUnderParent_ThrowsException() {
            UUID parentId = UUID.randomUUID();
            StoredFolder parent = new StoredFolder();
            parent.setId(parentId);
            parent.setName("Parent");
            parent.setStorageAccount(storageAccount);

            StoredFolder sibling = new StoredFolder();
            sibling.setName("Reports");
            parent.setChildFolders(new ArrayList<>(List.of(sibling)));

            StoredFolderDto dto = new StoredFolderDto();
            dto.setFolderName("reports");
            dto.setParentFolder(parentId);

            when(storageAccountRepository.findByUserId(userId)).thenReturn(Optional.of(storageAccount));
            when(folderRepository.getStoredFolderByFolderidAndUserId(parentId, userId)).thenReturn(Optional.of(parent));

            assertThatThrownBy(() -> folderService.createFolder(userId, dto))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("already exists in this location");
        }
    }

    @Nested
    @DisplayName("getAllFolders Tests")
    class GetAllFoldersTests {

        @Test
        @DisplayName("Successfully get all folders for user")
        void getAllFolders_Success() {
            StoredFolder folder1 = new StoredFolder();
            folder1.setId(UUID.randomUUID());
            folder1.setName("Folder1");
            folder1.setStorageAccount(storageAccount);

            StoredFolder folder2 = new StoredFolder();
            folder2.setId(UUID.randomUUID());
            folder2.setName("Folder2");
            folder2.setStorageAccount(storageAccount);

            storageAccount.setFolders(List.of(folder1, folder2));
            when(storageAccountRepository.findByUserId(userId)).thenReturn(Optional.of(storageAccount));

            List<StoredFolderDto> result = folderService.getAllFolders(userId);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getFolderName()).isEqualTo("Folder1");
            assertThat(result.get(1).getFolderName()).isEqualTo("Folder2");
        }

        @Test
        @DisplayName("Return empty list when account has no folders")
        void getAllFolders_EmptyList() {
            storageAccount.setFolders(Collections.emptyList());
            when(storageAccountRepository.findByUserId(userId)).thenReturn(Optional.of(storageAccount));

            List<StoredFolderDto> result = folderService.getAllFolders(userId);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Throw ResourceNotFoundException when storage account not found")
        void getAllFolders_AccountNotFound() {
            when(storageAccountRepository.findByUserId(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> folderService.getAllFolders(userId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Storage account not found");
        }
    }

    @Nested
    @DisplayName("getFolderById Tests")
    class GetFolderByIdTests {

        @Test
        @DisplayName("Successfully retrieve folder by ID with active files and subfolders")
        void getFolderById_Success() {
            UUID folderId = UUID.randomUUID();
            StoredFolder folder = new StoredFolder();
            folder.setId(folderId);
            folder.setName("Projects");
            folder.setStorageAccount(storageAccount);

            StoredFolder childFolder = new StoredFolder();
            childFolder.setId(UUID.randomUUID());
            childFolder.setName("Dropzone");
            childFolder.setStorageAccount(storageAccount);
            childFolder.setParentFolder(folder);
            folder.setChildFolders(List.of(childFolder));

            StoredFile activeFile = new StoredFile();
            activeFile.setId(UUID.randomUUID());
            activeFile.setFileName("readme.md");
            activeFile.setFileSize(2048L);
            activeFile.setStatus(FileStatus.ACTIVE);
            activeFile.setContentType("text/markdown");
            activeFile.setFolder(folder);

            when(folderRepository.getStoredFolderByFolderidAndUserId(folderId, userId)).thenReturn(Optional.of(folder));
            when(fileRepository.getUserFilesInsideFolder(userId, folderId, FileStatus.ACTIVE)).thenReturn(List.of(activeFile));

            StoredFolderDto result = folderService.getFolderById(userId, folderId);

            assertThat(result).isNotNull();
            assertThat(result.getFolderName()).isEqualTo("Projects");
            assertThat(result.getFileFolderDto()).isNotNull();
            assertThat(result.getFileFolderDto().getFilesInsideFolderList()).hasSize(1);
            assertThat(result.getFileFolderDto().getFilesInsideFolderList().get(0).getFileName()).isEqualTo("readme.md");
            assertThat(result.getFileFolderDto().getFolderInsideFolderList()).hasSize(1);
            assertThat(result.getFileFolderDto().getFolderInsideFolderList().get(0).getFolderName()).isEqualTo("Dropzone");
        }

        @Test
        @DisplayName("Throw ResourceNotFoundException when folder not found")
        void getFolderById_NotFound() {
            UUID folderId = UUID.randomUUID();
            when(folderRepository.getStoredFolderByFolderidAndUserId(folderId, userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> folderService.getFolderById(userId, folderId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Folder not found");
        }
    }

    @Nested
    @DisplayName("renameFolder Tests")
    class RenameFolderTests {

        @Test
        @DisplayName("Successfully rename folder")
        void renameFolder_Success() {
            UUID folderId = UUID.randomUUID();
            StoredFolder folder = new StoredFolder();
            folder.setId(folderId);
            folder.setName("OldName");
            folder.setStorageAccount(storageAccount);
            storageAccount.setFolders(new ArrayList<>(List.of(folder)));

            when(folderRepository.getStoredFolderByFolderidAndUserId(folderId, userId)).thenReturn(Optional.of(folder));

            String result = folderService.renameFolder(userId, folderId, "NewName");

            assertThat(result).isEqualTo("Folder renamed successfully to: NewName");
            assertThat(folder.getName()).isEqualTo("NewName");
            verify(folderRepository).save(folder);
        }

        @Test
        @DisplayName("Rename to same name is a no-op save")
        void renameFolder_SameNameCaseInsensitive_NoOp() {
            UUID folderId = UUID.randomUUID();
            StoredFolder folder = new StoredFolder();
            folder.setId(folderId);
            folder.setName("ExistingName");
            folder.setStorageAccount(storageAccount);

            when(folderRepository.getStoredFolderByFolderidAndUserId(folderId, userId)).thenReturn(Optional.of(folder));

            String result = folderService.renameFolder(userId, folderId, "existingname");

            assertThat(result).isEqualToIgnoringCase("Folder renamed successfully to: existingname");
            verify(folderRepository, never()).save(any());
        }

        @Test
        @DisplayName("Throw DuplicateResourceException when renaming to duplicate sibling name")
        void renameFolder_DuplicateSiblingName_ThrowsException() {
            UUID folderId = UUID.randomUUID();
            StoredFolder folderToRename = new StoredFolder();
            folderToRename.setId(folderId);
            folderToRename.setName("FolderA");
            folderToRename.setStorageAccount(storageAccount);

            StoredFolder sibling = new StoredFolder();
            sibling.setId(UUID.randomUUID());
            sibling.setName("FolderB");
            sibling.setStorageAccount(storageAccount);

            storageAccount.setFolders(new ArrayList<>(List.of(folderToRename, sibling)));

            when(folderRepository.getStoredFolderByFolderidAndUserId(folderId, userId)).thenReturn(Optional.of(folderToRename));

            assertThatThrownBy(() -> folderService.renameFolder(userId, folderId, "folderb"))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("already exists in this location");

            verify(folderRepository, never()).save(folderToRename);
        }

        @Test
        @DisplayName("Throw MethodArgumentNotValidException when new name is invalid")
        void renameFolder_InvalidName_ThrowsException() {
            UUID folderId = UUID.randomUUID();
            StoredFolder folder = new StoredFolder();
            folder.setId(folderId);
            folder.setName("ValidName");

            when(folderRepository.getStoredFolderByFolderidAndUserId(folderId, userId)).thenReturn(Optional.of(folder));

            assertThatThrownBy(() -> folderService.renameFolder(userId, folderId, "   "))
                    .isInstanceOf(MethodArgumentNotValidException.class)
                    .hasMessageContaining("New folder name cannot be empty");

            assertThatThrownBy(() -> folderService.renameFolder(userId, folderId, "invalid/name"))
                    .isInstanceOf(MethodArgumentNotValidException.class)
                    .hasMessageContaining("prohibited characters");
        }
    }

    @Nested
    @DisplayName("deleteFolder Tests")
    class DeleteFolderTests {

        @Test
        @DisplayName("Successfully delete folder recursively with active files and subfolders")
        void deleteFolder_Recursive_Success() {
            UUID parentId = UUID.randomUUID();
            StoredFolder parent = new StoredFolder();
            parent.setId(parentId);
            parent.setName("Parent");

            UUID childId = UUID.randomUUID();
            StoredFolder child = new StoredFolder();
            child.setId(childId);
            child.setName("Child");
            child.setChildFolders(new ArrayList<>());
            parent.setChildFolders(new ArrayList<>(List.of(child)));

            StoredFile parentFile = new StoredFile();
            parentFile.setId(UUID.randomUUID());
            parentFile.setStatus(FileStatus.ACTIVE);
            parentFile.setFolder(parent);

            StoredFile childFile = new StoredFile();
            childFile.setId(UUID.randomUUID());
            childFile.setStatus(FileStatus.ACTIVE);
            childFile.setFolder(child);

            when(folderRepository.getStoredFolderByFolderidAndUserId(parentId, userId)).thenReturn(Optional.of(parent));
            when(fileRepository.getUserFilesInsideFolder(userId, parentId, FileStatus.ACTIVE)).thenReturn(List.of(parentFile));
            when(fileRepository.getUserFilesInsideFolder(userId, childId, FileStatus.ACTIVE)).thenReturn(List.of(childFile));

            String result = folderService.deleteFolder(userId, parentId);

            assertThat(result).isEqualTo("Folder deleted successfully");

            assertThat(parentFile.getStatus()).isEqualTo(FileStatus.DELETED);
            assertThat(parentFile.getFolder()).isNull();
            assertThat(childFile.getStatus()).isEqualTo(FileStatus.DELETED);
            assertThat(childFile.getFolder()).isNull();

            verify(fileRepository).save(parentFile);
            verify(fileRepository).save(childFile);
            verify(folderRepository).delete(child);
            verify(folderRepository).delete(parent);
        }

        @Test
        @DisplayName("Throw ResourceNotFoundException when deleting nonexistent folder")
        void deleteFolder_NotFound_ThrowsException() {
            UUID folderId = UUID.randomUUID();
            when(folderRepository.getStoredFolderByFolderidAndUserId(folderId, userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> folderService.deleteFolder(userId, folderId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Folder not found");
        }
    }

    @Nested
    @DisplayName("moveFolder Tests")
    class MoveFolderTests {

        @Test
        @DisplayName("Successfully move folder to root")
        void moveFolder_ToRoot_Success() {
            UUID folderId = UUID.randomUUID();
            StoredFolder folder = new StoredFolder();
            folder.setId(folderId);
            folder.setName("MovedFolder");
            folder.setStorageAccount(storageAccount);
            storageAccount.setFolders(new ArrayList<>(List.of(folder)));

            StoredFolderDto dto = new StoredFolderDto();
            dto.setParentFolder(null);

            when(folderRepository.getStoredFolderByFolderidAndUserId(folderId, userId)).thenReturn(Optional.of(folder));

            String result = folderService.moveFolder(userId, folderId, dto);

            assertThat(result).isEqualTo("Folder moved successfully");
            assertThat(folder.getParentFolder()).isNull();
            verify(folderRepository).save(folder);
        }

        @Test
        @DisplayName("Successfully move folder to another parent")
        void moveFolder_ToNewParent_Success() {
            UUID folderId = UUID.randomUUID();
            StoredFolder folder = new StoredFolder();
            folder.setId(folderId);
            folder.setName("FolderA");
            folder.setStorageAccount(storageAccount);

            UUID destinationId = UUID.randomUUID();
            StoredFolder destination = new StoredFolder();
            destination.setId(destinationId);
            destination.setName("Destination");
            destination.setStorageAccount(storageAccount);
            destination.setChildFolders(new ArrayList<>());

            StoredFolderDto dto = new StoredFolderDto();
            dto.setParentFolder(destinationId);

            when(folderRepository.getStoredFolderByFolderidAndUserId(folderId, userId)).thenReturn(Optional.of(folder));
            when(folderRepository.getStoredFolderByFolderidAndUserId(destinationId, userId)).thenReturn(Optional.of(destination));

            String result = folderService.moveFolder(userId, folderId, dto);

            assertThat(result).isEqualTo("Folder moved successfully");
            assertThat(folder.getParentFolder()).isEqualTo(destination);
            verify(folderRepository).save(folder);
        }

        @Test
        @DisplayName("Throw HierarchyCycleException when moving folder into itself")
        void moveFolder_IntoSelf_ThrowsException() {
            UUID folderId = UUID.randomUUID();
            StoredFolder folder = new StoredFolder();
            folder.setId(folderId);
            folder.setName("FolderA");

            StoredFolderDto dto = new StoredFolderDto();
            dto.setParentFolder(folderId);

            when(folderRepository.getStoredFolderByFolderidAndUserId(folderId, userId)).thenReturn(Optional.of(folder));

            assertThatThrownBy(() -> folderService.moveFolder(userId, folderId, dto))
                    .isInstanceOf(HierarchyCycleException.class)
                    .hasMessageContaining("Cannot move or copy a folder into itself");
        }

        @Test
        @DisplayName("Throw HierarchyCycleException when moving folder into descendant")
        void moveFolder_IntoDescendant_ThrowsException() {
            UUID parentId = UUID.randomUUID();
            StoredFolder parent = new StoredFolder();
            parent.setId(parentId);
            parent.setName("Parent");

            UUID childId = UUID.randomUUID();
            StoredFolder child = new StoredFolder();
            child.setId(childId);
            child.setName("Child");
            child.setParentFolder(parent);

            UUID grandChildId = UUID.randomUUID();
            StoredFolder grandChild = new StoredFolder();
            grandChild.setId(grandChildId);
            grandChild.setName("GrandChild");
            grandChild.setParentFolder(child);

            StoredFolderDto dto = new StoredFolderDto();
            dto.setParentFolder(grandChildId);

            when(folderRepository.getStoredFolderByFolderidAndUserId(parentId, userId)).thenReturn(Optional.of(parent));
            when(folderRepository.getStoredFolderByFolderidAndUserId(grandChildId, userId)).thenReturn(Optional.of(grandChild));

            assertThatThrownBy(() -> folderService.moveFolder(userId, parentId, dto))
                    .isInstanceOf(HierarchyCycleException.class)
                    .hasMessageContaining("Cannot move or copy a folder into one of its descendants");
        }

        @Test
        @DisplayName("Throw DuplicateResourceException when move causes sibling conflict in destination")
        void moveFolder_DuplicateSiblingInDestination_ThrowsException() {
            UUID folderId = UUID.randomUUID();
            StoredFolder folder = new StoredFolder();
            folder.setId(folderId);
            folder.setName("Docs");
            folder.setStorageAccount(storageAccount);

            UUID destinationId = UUID.randomUUID();
            StoredFolder destination = new StoredFolder();
            destination.setId(destinationId);
            destination.setName("Destination");
            destination.setStorageAccount(storageAccount);

            StoredFolder existingChild = new StoredFolder();
            existingChild.setId(UUID.randomUUID());
            existingChild.setName("docs"); // same name
            destination.setChildFolders(new ArrayList<>(List.of(existingChild)));

            StoredFolderDto dto = new StoredFolderDto();
            dto.setParentFolder(destinationId);

            when(folderRepository.getStoredFolderByFolderidAndUserId(folderId, userId)).thenReturn(Optional.of(folder));
            when(folderRepository.getStoredFolderByFolderidAndUserId(destinationId, userId)).thenReturn(Optional.of(destination));

            assertThatThrownBy(() -> folderService.moveFolder(userId, folderId, dto))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("already exists in this location");
        }
    }

    @Nested
    @DisplayName("copyFolder Tests")
    class CopyFolderTests {

        @Test
        @DisplayName("Successfully copy folder with default name '(Copy)'")
        void copyFolder_DefaultName_Success() {
            UUID sourceId = UUID.randomUUID();
            StoredFolder source = new StoredFolder();
            source.setId(sourceId);
            source.setName("Designs");
            source.setStorageAccount(storageAccount);
            source.setChildFolders(new ArrayList<>());

            StoredFile file1 = new StoredFile();
            file1.setId(UUID.randomUUID());
            file1.setFileName("logo.png");
            file1.setFileSize(500L);
            file1.setStorageKey("key-1");
            file1.setChecksum("hash-1");
            file1.setStorageAccount(storageAccount);

            StoredFolder copiedFolder = new StoredFolder();
            copiedFolder.setId(UUID.randomUUID());
            copiedFolder.setName("Designs (Copy)");

            when(storageAccountRepository.findByUserId(userId)).thenReturn(Optional.of(storageAccount));
            when(folderRepository.getStoredFolderByFolderidAndUserId(sourceId, userId)).thenReturn(Optional.of(source));
            when(fileRepository.getUserFilesInsideFolder(userId, sourceId, FileStatus.ACTIVE)).thenReturn(List.of(file1));
            when(folderRepository.save(any(StoredFolder.class))).thenReturn(copiedFolder);

            String result = folderService.copyFolder(userId, sourceId, null);

            assertThat(result).isEqualTo("Folder copied successfully");
            verify(folderRepository).save(argThat(f -> "Designs (Copy)".equals(f.getName())));
            verify(fileRepository).save(argThat(f -> "logo.png".equals(f.getFileName()) && f.getFolder() == copiedFolder));
            verify(storageAccountRepository).save(storageAccount);
        }

        @Test
        @DisplayName("Successfully copy folder with custom name")
        void copyFolder_CustomName_Success() {
            UUID sourceId = UUID.randomUUID();
            StoredFolder source = new StoredFolder();
            source.setId(sourceId);
            source.setName("Designs");
            source.setStorageAccount(storageAccount);
            source.setChildFolders(new ArrayList<>());

            StoredFolderDto dto = new StoredFolderDto();
            dto.setFolderName("Designs_v2");

            StoredFolder copiedFolder = new StoredFolder();
            copiedFolder.setId(UUID.randomUUID());
            copiedFolder.setName("Designs_v2");

            when(storageAccountRepository.findByUserId(userId)).thenReturn(Optional.of(storageAccount));
            when(folderRepository.getStoredFolderByFolderidAndUserId(sourceId, userId)).thenReturn(Optional.of(source));
            when(fileRepository.getUserFilesInsideFolder(userId, sourceId, FileStatus.ACTIVE)).thenReturn(Collections.emptyList());
            when(folderRepository.save(any(StoredFolder.class))).thenReturn(copiedFolder);

            String result = folderService.copyFolder(userId, sourceId, dto);

            assertThat(result).isEqualTo("Folder copied successfully");
            verify(folderRepository).save(argThat(f -> "Designs_v2".equals(f.getName())));
        }

        @Test
        @DisplayName("Throw QuotaExceededException when copying folder causes quota to exceed")
        void copyFolder_QuotaExceeded_ThrowsException() {
            UUID sourceId = UUID.randomUUID();
            StoredFolder source = new StoredFolder();
            source.setId(sourceId);
            source.setName("HugeFolder");
            source.setStorageAccount(storageAccount);
            source.setChildFolders(new ArrayList<>());

            storageAccount.setQuotaBytes(1000L);
            storageAccount.setUsedBytes(900L);

            StoredFile largeFile = new StoredFile();
            largeFile.setId(UUID.randomUUID());
            largeFile.setFileSize(200L); // 900 + 200 > 1000

            when(storageAccountRepository.findByUserId(userId)).thenReturn(Optional.of(storageAccount));
            when(folderRepository.getStoredFolderByFolderidAndUserId(sourceId, userId)).thenReturn(Optional.of(source));
            when(fileRepository.getUserFilesInsideFolder(userId, sourceId, FileStatus.ACTIVE)).thenReturn(List.of(largeFile));

            assertThatThrownBy(() -> folderService.copyFolder(userId, sourceId, null))
                    .isInstanceOf(QuotaExceededException.class)
                    .hasMessageContaining("storage quota exceeded");

            verify(folderRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("getFolderBreadcrumb Tests")
    class GetFolderBreadcrumbTests {

        @Test
        @DisplayName("Return root breadcrumb when folderId is null")
        void getFolderBreadcrumb_NullFolderId_ReturnsRoot() {
            List<BreadcrumbItemDto> breadcrumbs = folderService.getFolderBreadcrumb(userId, null);

            assertThat(breadcrumbs).hasSize(1);
            assertThat(breadcrumbs.get(0).getId()).isNull();
            assertThat(breadcrumbs.get(0).getName()).isEqualTo("My Drive");
        }

        @Test
        @DisplayName("Return full hierarchy path for nested folder")
        void getFolderBreadcrumb_NestedFolder_ReturnsFullPath() {
            UUID rootFolderId = UUID.randomUUID();
            StoredFolder rootFolder = new StoredFolder();
            rootFolder.setId(rootFolderId);
            rootFolder.setName("Root");
            rootFolder.setParentFolder(null);

            UUID childFolderId = UUID.randomUUID();
            StoredFolder childFolder = new StoredFolder();
            childFolder.setId(childFolderId);
            childFolder.setName("SubFolder");
            childFolder.setParentFolder(rootFolder);

            when(folderRepository.getStoredFolderByFolderidAndUserId(childFolderId, userId)).thenReturn(Optional.of(childFolder));

            List<BreadcrumbItemDto> breadcrumbs = folderService.getFolderBreadcrumb(userId, childFolderId);

            assertThat(breadcrumbs).hasSize(3);
            assertThat(breadcrumbs.get(0).getName()).isEqualTo("My Drive");
            assertThat(breadcrumbs.get(1).getName()).isEqualTo("Root");
            assertThat(breadcrumbs.get(2).getName()).isEqualTo("SubFolder");
            assertThat(breadcrumbs.get(2).getId()).isEqualTo(childFolderId);
        }

        @Test
        @DisplayName("Throw ResourceNotFoundException when folder does not exist")
        void getFolderBreadcrumb_NotFound_ThrowsException() {
            UUID folderId = UUID.randomUUID();
            when(folderRepository.getStoredFolderByFolderidAndUserId(folderId, userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> folderService.getFolderBreadcrumb(userId, folderId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Folder not found");
        }

        @Test
        @DisplayName("Throw HierarchyCycleException when circular ancestor reference detected (>100)")
        void getFolderBreadcrumb_CycleDetected_ThrowsException() {
            UUID folderId = UUID.randomUUID();
            StoredFolder folderA = new StoredFolder();
            folderA.setId(folderId);
            folderA.setName("LoopA");

            StoredFolder folderB = new StoredFolder();
            folderB.setId(UUID.randomUUID());
            folderB.setName("LoopB");

            folderA.setParentFolder(folderB);
            folderB.setParentFolder(folderA); // circular reference

            when(folderRepository.getStoredFolderByFolderidAndUserId(folderId, userId)).thenReturn(Optional.of(folderA));

            assertThatThrownBy(() -> folderService.getFolderBreadcrumb(userId, folderId))
                    .isInstanceOf(HierarchyCycleException.class)
                    .hasMessageContaining("Infinite cycle detected in folder ancestors");
        }
    }
}
