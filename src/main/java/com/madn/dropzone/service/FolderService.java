package com.madn.dropzone.service;

import com.madn.dropzone.dto.BreadcrumbItemDto;
import com.madn.dropzone.dto.FileFolderDto;
import com.madn.dropzone.dto.StoredFileDto;
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
import com.madn.dropzone.mapper.FileMapper;
import com.madn.dropzone.mapper.FolderMapper;
import com.madn.dropzone.repository.FileRepository;
import com.madn.dropzone.repository.FolderRepository;
import com.madn.dropzone.repository.StorageAccountRepository;
import com.madn.dropzone.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@AllArgsConstructor
public class FolderService {

    private final FolderRepository folderRepository;
    private final FileRepository fileRepository;
    private final StorageAccountRepository storageAccountRepository;
    private final UserRepository userRepository;

    @Transactional
    public String createFolder(UUID userId, StoredFolderDto storedFolderDto) {
        StorageAccount account = storageAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Storage account not found for user: " + userId));

        if (storedFolderDto == null || storedFolderDto.getFolderName() == null || storedFolderDto.getFolderName().trim().isEmpty()) {
            throw new MethodArgumentNotValidException("Folder name cannot be empty");
        }

        String folderName = storedFolderDto.getFolderName().trim();
        validateFolderName(folderName);

        StoredFolder parent = null;
        if (storedFolderDto.getParentFolder() != null) {
            parent = folderRepository.getStoredFolderByFolderidAndUserId(storedFolderDto.getParentFolder(), userId)
                    .orElseThrow(() -> new InvalidParentException("Parent folder does not exist or access is denied"));
        }

        validateUniqueSiblingName(account, parent, folderName, null);

        StoredFolder folder = new StoredFolder();
        folder.setName(folderName);
        folder.setStorageAccount(account);
        folder.setParentFolder(parent);

        StoredFolder savedFolder = folderRepository.save(folder);
        return "Folder created successfully: " + savedFolder.getId();
    }

    @Transactional(readOnly = true)
    public List<StoredFolderDto> getAllFolders(UUID userId) {
        StorageAccount account = storageAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Storage account not found for user: " + userId));

        List<StoredFolder> folders = account.getFolders();
        if (folders == null || folders.isEmpty()) {
            return Collections.emptyList();
        }

        return folders.stream()
                .map(FolderMapper::folderToDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public StoredFolderDto getFolderById(UUID userId, UUID folderId) {
        StoredFolder folder = folderRepository.getStoredFolderByFolderidAndUserId(folderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + folderId));

        StoredFolderDto dto = FolderMapper.folderToDto(folder);
        dto.setFileFolderDto(getFolderContents(userId, folderId));
        return dto;
    }

    @Transactional
    public String renameFolder(UUID userId, UUID folderId, String name) {
        StoredFolder folder = folderRepository.getStoredFolderByFolderidAndUserId(folderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + folderId));

        if (name == null || name.trim().isEmpty()) {
            throw new MethodArgumentNotValidException("New folder name cannot be empty");
        }

        String newName = name.trim();
        validateFolderName(newName);

        if (!folder.getName().equalsIgnoreCase(newName)) {
            validateUniqueSiblingName(folder.getStorageAccount(), folder.getParentFolder(), newName, folder.getId());
            folder.setName(newName);
            folderRepository.save(folder);
        }

        return "Folder renamed successfully to: " + newName;
    }

    @Transactional
    public String deleteFolder(UUID userId, UUID folderId) {
        StoredFolder rootFolder = folderRepository.getStoredFolderByFolderidAndUserId(folderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + folderId));

        deleteFolderRecursively(userId, rootFolder);

        return "Folder deleted successfully";
    }

    @Transactional(readOnly = true)
    public FileFolderDto getFolderContents(UUID userId, UUID folderId) {
        StoredFolder folder = folderRepository.getStoredFolderByFolderidAndUserId(folderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + folderId));

        List<StoredFile> activeFiles = fileRepository.getUserFilesInsideFolder(userId, folderId, FileStatus.ACTIVE);
        List<StoredFileDto> fileDtos = (activeFiles != null)
                ? activeFiles.stream().map(FileMapper::fileToFileDto).toList()
                : Collections.emptyList();

        List<StoredFolder> childFolders = folder.getChildFolders();
        List<StoredFolderDto> folderDtos = (childFolders != null)
                ? childFolders.stream().map(FolderMapper::folderToDto).toList()
                : Collections.emptyList();

        return new FileFolderDto(fileDtos, folderDtos);
    }

    @Transactional
    public String moveFolder(UUID userId, UUID folderId, StoredFolderDto storedFolderDto) {
        StoredFolder folder = folderRepository.getStoredFolderByFolderidAndUserId(folderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + folderId));

        UUID destinationFolderId = storedFolderDto != null ? storedFolderDto.getParentFolder() : null;

        StoredFolder destination = null;
        if (destinationFolderId != null) {
            destination = folderRepository.getStoredFolderByFolderidAndUserId(destinationFolderId, userId)
                    .orElseThrow(() -> new InvalidParentException("Destination folder does not exist or access is denied: " + destinationFolderId));

            validateNotMovingIntoSelfOrDescendant(folder, destination);
        }

        validateUniqueSiblingName(folder.getStorageAccount(), destination, folder.getName(), folder.getId());

        folder.setParentFolder(destination);
        folderRepository.save(folder);

        return "Folder moved successfully";
    }

    @Transactional
    public String copyFolder(UUID userId, UUID folderId, StoredFolderDto storedFolderDto) {
        StorageAccount account = storageAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Storage account not found for user: " + userId));

        StoredFolder sourceFolder = folderRepository.getStoredFolderByFolderidAndUserId(folderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + folderId));

        UUID destinationFolderId = storedFolderDto != null ? storedFolderDto.getParentFolder() : null;

        StoredFolder destination = null;
        if (destinationFolderId != null) {
            destination = folderRepository.getStoredFolderByFolderidAndUserId(destinationFolderId, userId)
                    .orElseThrow(() -> new InvalidParentException("Destination folder does not exist or access is denied: " + destinationFolderId));

            validateNotMovingIntoSelfOrDescendant(sourceFolder, destination);
        }

        String copyName;
        if (storedFolderDto != null && storedFolderDto.getFolderName() != null && !storedFolderDto.getFolderName().trim().isEmpty()) {
            copyName = storedFolderDto.getFolderName().trim();
            validateFolderName(copyName);
        } else {
            copyName = sourceFolder.getName() + " (Copy)";
        }

        validateUniqueSiblingName(account, destination, copyName, null);

        long totalSubtreeBytes = calculateSubtreeActiveFileSize(userId, sourceFolder);
        if (account.getUsedBytes() + totalSubtreeBytes > account.getQuotaBytes()) {
            throw new QuotaExceededException("Cannot copy folder: storage quota exceeded");
        }

        deepCopyFolder(userId, sourceFolder, destination, copyName, account);
        storageAccountRepository.save(account);

        return "Folder copied successfully";
    }

    @Transactional(readOnly = true)
    public List<BreadcrumbItemDto> getFolderBreadcrumb(UUID userId, UUID folderId) {
        if (folderId == null) {
            return List.of(BreadcrumbItemDto.builder().id(null).name("My Drive").build());
        }

        StoredFolder current = folderRepository.getStoredFolderByFolderidAndUserId(folderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + folderId));

        LinkedList<BreadcrumbItemDto> breadcrumbs = new LinkedList<>();
        int safetyLimit = 0;

        while (current != null) {
            if (++safetyLimit > 100) {
                throw new HierarchyCycleException("Infinite cycle detected in folder ancestors");
            }
            breadcrumbs.addFirst(BreadcrumbItemDto.builder()
                    .id(current.getId())
                    .name(current.getName())
                    .build());
            current = current.getParentFolder();
        }

        breadcrumbs.addFirst(BreadcrumbItemDto.builder().id(null).name("My Drive").build());
        return breadcrumbs;
    }

    private void deleteFolderRecursively(UUID userId, StoredFolder currentFolder) {
        List<StoredFolder> children = currentFolder.getChildFolders();
        if (children != null) {
            List<StoredFolder> childList = new ArrayList<>(children);
            for (StoredFolder child : childList) {
                deleteFolderRecursively(userId, child);
            }
        }

        List<StoredFile> files = fileRepository.getUserFilesInsideFolder(userId, currentFolder.getId(), FileStatus.ACTIVE);
        if (files != null) {
            for (StoredFile file : files) {
                file.setStatus(FileStatus.DELETED);
                file.setFolder(null);
                fileRepository.save(file);
            }
        }

        folderRepository.delete(currentFolder);
    }

    private void validateNotMovingIntoSelfOrDescendant(StoredFolder source, StoredFolder destination) {
        if (source.getId().equals(destination.getId())) {
            throw new HierarchyCycleException("Cannot move or copy a folder into itself");
        }

        StoredFolder ancestor = destination.getParentFolder();
        int depth = 0;
        while (ancestor != null) {
            if (++depth > 100) {
                throw new HierarchyCycleException("Cycle detected in ancestor tree");
            }
            if (ancestor.getId().equals(source.getId())) {
                throw new HierarchyCycleException("Cannot move or copy a folder into one of its descendants");
            }
            ancestor = ancestor.getParentFolder();
        }
    }

    private void validateUniqueSiblingName(StorageAccount account, StoredFolder parent, String name, UUID excludeFolderId) {
        if (parent != null) {
            List<StoredFolder> siblings = parent.getChildFolders();
            if (siblings != null) {
                for (StoredFolder sibling : siblings) {
                    if (excludeFolderId != null && sibling.getId().equals(excludeFolderId)) {
                        continue;
                    }
                    if (sibling.getName().equalsIgnoreCase(name)) {
                        throw new DuplicateResourceException("A folder with name '" + name + "' already exists in this location");
                    }
                }
            }
        } else {
            List<StoredFolder> accountFolders = account.getFolders();
            if (accountFolders != null) {
                for (StoredFolder sibling : accountFolders) {
                    if (excludeFolderId != null && sibling.getId().equals(excludeFolderId)) {
                        continue;
                    }
                    if (sibling.getParentFolder() == null && sibling.getName().equalsIgnoreCase(name)) {
                        throw new DuplicateResourceException("A folder with name '" + name + "' already exists in this location");
                    }
                }
            }
        }
    }

    private void validateFolderName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new MethodArgumentNotValidException("Folder name cannot be empty");
        }
        if (name.length() > 255) {
            throw new MethodArgumentNotValidException("Folder name cannot exceed 255 characters");
        }
        if (name.matches(".*[\\\\/:*?\"<>|].*")) {
            throw new MethodArgumentNotValidException("Folder name contains prohibited characters (\\ / : * ? \" < > |)");
        }
    }

    private long calculateSubtreeActiveFileSize(UUID userId, StoredFolder folder) {
        long total = 0L;
        List<StoredFile> files = fileRepository.getUserFilesInsideFolder(userId, folder.getId(), FileStatus.ACTIVE);
        if (files != null) {
            for (StoredFile file : files) {
                total += file.getFileSize();
            }
        }
        List<StoredFolder> children = folder.getChildFolders();
        if (children != null) {
            for (StoredFolder child : children) {
                total += calculateSubtreeActiveFileSize(userId, child);
            }
        }
        return total;
    }

    private void deepCopyFolder(UUID userId, StoredFolder source, StoredFolder newParent, String newName, StorageAccount account) {
        StoredFolder copiedFolder = new StoredFolder();
        copiedFolder.setName(newName);
        copiedFolder.setStorageAccount(account);
        copiedFolder.setParentFolder(newParent);
        StoredFolder savedFolder = folderRepository.save(copiedFolder);

        List<StoredFile> files = fileRepository.getUserFilesInsideFolder(userId, source.getId(), FileStatus.ACTIVE);
        if (files != null) {
            for (StoredFile file : files) {
                StoredFile fileClone = FileMapper.StoredFileCopy(file, savedFolder);
                account.storeFileInAccount(fileClone);
                fileRepository.save(fileClone);
            }
        }

        List<StoredFolder> children = source.getChildFolders();
        if (children != null) {
            for (StoredFolder child : children) {
                deepCopyFolder(userId, child, savedFolder, child.getName(), account);
            }
        }
    }
}
