package com.madn.dropzone.service;

import com.madn.dropzone.dto.BinFileDto;
import com.madn.dropzone.dto.StoredFileDto;
import com.madn.dropzone.entity.StorageAccount;
import com.madn.dropzone.entity.StoredFile;
import com.madn.dropzone.entity.StoredFolder;
import com.madn.dropzone.enums.FileStatus;
import com.madn.dropzone.exception.common.ResourceNotFoundException;
import com.madn.dropzone.exception.validation.MethodArgumentNotValidException;
import com.madn.dropzone.mapper.FileMapper;
import com.madn.dropzone.repository.FileRepository;
import com.madn.dropzone.repository.FolderRepository;
import com.madn.dropzone.repository.PhysicalStorageObjectRepository;
import com.madn.dropzone.repository.StorageAccountRepository;
import com.madn.dropzone.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class BinService {

    private final UserRepository userRepository;
    private final FileRepository fileRepository;
    private final StorageAccountRepository storageAccountRepository;
    private final StorageService storageService;
    private final PhysicalStorageObjectRepository physicalStorageObjectRepository;

    @Autowired(required = false)
    private FolderRepository folderRepository;

    public BinService(UserRepository userRepository,
                      FileRepository fileRepository,
                      StorageAccountRepository storageAccountRepository,
                      StorageService storageService,
                      @Autowired(required = false) PhysicalStorageObjectRepository physicalStorageObjectRepository) {
        this.userRepository = userRepository;
        this.fileRepository = fileRepository;
        this.storageAccountRepository = storageAccountRepository;
        this.storageService = storageService;
        this.physicalStorageObjectRepository = physicalStorageObjectRepository;
    }

    public List<StoredFileDto> getFilesInBin(UUID userid, String type) {
        boolean check = userRepository.existsById(userid);
        if (check) {
            return fileRepository.getStoredFileFromStatus(userid, FileStatus.DELETED, "%" + type + "%")
                    .stream()
                    .map(FileMapper::fileToFileDto)
                    .toList();
        } else {
            throw new ResourceNotFoundException("User Does not Exists !!");
        }
    }

    @Transactional
    public String restoreFiles(UUID userid, BinFileDto binFileDto) {
        if (binFileDto == null || binFileDto.getFileIDs() == null || binFileDto.getFileIDs().isEmpty()) {
            return "Nothing to restore";
        }

        List<UUID> uniqueFileIds = new ArrayList<>(
                new LinkedHashSet<>(binFileDto.getFileIDs())
        );

        List<StoredFile> filesToRestore = new ArrayList<>();

        for (UUID fileId : uniqueFileIds) {
            StoredFile file = fileRepository
                    .getStoredFileByIdAndUserId(userid, fileId)
                    .orElseThrow(() ->
                            new ResourceNotFoundException("File not found: " + fileId));

            if (file.getStatus() != FileStatus.DELETED) {
                throw new MethodArgumentNotValidException(
                        "Only deleted files can be restored: " + fileId
                );
            }

            // Verify underlying storage object is still valid and readable before restoring
            try {
                storageService.getFilePath(file);
            } catch (Exception e) {
                throw new ResourceNotFoundException("Cannot restore file " + fileId + ": underlying storage object is no longer valid");
            }

            file.setStatus(FileStatus.ACTIVE);
            filesToRestore.add(file);
        }

        fileRepository.saveAll(filesToRestore);
        return "Selected files restored successfully!";
    }

    @Transactional
    public String restoreAllFiles(UUID userid) {
        List<StoredFile> fileList =
                fileRepository.getStoredFileFromStatus(
                        userid,
                        FileStatus.DELETED,
                        ""
                );

        if (folderRepository != null) {
            StorageAccount account = storageAccountRepository.findByUserId(userid).orElse(null);
            if (account != null) {
                List<StoredFolder> deletedFolders = folderRepository.findByStorageAccountIdAndStatus(account.getId(), FileStatus.DELETED);
                for (StoredFolder folder : deletedFolders) {
                    folder.setStatus(FileStatus.ACTIVE);
                }
                folderRepository.saveAll(deletedFolders);
            }
        }

        if (fileList.isEmpty()) {
            return "Nothing to restore";
        }

        for (StoredFile file : fileList) {
            // Verify underlying storage object is still valid
            try {
                storageService.getFilePath(file);
                file.setStatus(FileStatus.ACTIVE);
            } catch (Exception ignored) {
                // If invalid, keep deleted
            }
        }

        fileRepository.saveAll(fileList);
        return "All files have been restored successfully!";
    }

    @Transactional
    public String deleteFiles(UUID userid, BinFileDto binFileDto) {
        StorageAccount account = storageAccountRepository
                .findByUserId(userid)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User does not exist"));

        if (binFileDto == null || binFileDto.getFileIDs() == null || binFileDto.getFileIDs().isEmpty()) {
            throw new MethodArgumentNotValidException("No file IDs provided");
        }

        // Prevent processing duplicate file IDs in the same request
        List<UUID> uniqueFileIds = new ArrayList<>(
                new LinkedHashSet<>(binFileDto.getFileIDs())
        );

        List<StoredFile> fileList = new ArrayList<>();

        for (UUID fileId : uniqueFileIds) {
            StoredFile file = fileRepository
                    .getStoredFileByIdAndUserId(userid, fileId)
                    .orElseThrow(() ->
                            new ResourceNotFoundException("File not found: " + fileId));

            if (file.getStatus() != FileStatus.DELETED) {
                throw new MethodArgumentNotValidException(
                        "Only deleted files can be permanently removed: " + fileId
                );
            }

            fileList.add(file);
        }

        purgeFilesFromBin(fileList, account);
        return "Files deleted successfully!";
    }

    @Transactional
    public String deleteAllFiles(UUID userid) {
        StorageAccount account = storageAccountRepository
                .findByUserId(userid)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User does not exist"));

        List<StoredFile> fileList = fileRepository
                .getStoredFileFromStatus(userid, FileStatus.DELETED, "");

        if (folderRepository != null) {
            List<StoredFolder> deletedFolders = folderRepository.findByStorageAccountIdAndStatus(account.getId(), FileStatus.DELETED);
            for (StoredFolder folder : deletedFolders) {
                folderRepository.delete(folder);
            }
        }

        if (fileList.isEmpty()) {
            return "Bin is already empty!";
        }

        purgeFilesFromBin(fileList, account);
        return "All files deleted successfully!";
    }

    @com.madn.dropzone.aspect.AuditAction(action = "BIN_RESTORE")
    @Transactional
    public void restoreFiles(UUID userId, com.madn.dropzone.dto.request.BinActionRequest request) {
        if (request == null || request.getFileIds() == null || request.getFileIds().isEmpty()) {
            return;
        }
        BinFileDto dto = new BinFileDto();
        dto.setFileIDs(request.getFileIds());
        restoreFiles(userId, dto);
    }

    @com.madn.dropzone.aspect.AuditAction(action = "BIN_PURGE")
    @Transactional
    public void deleteFiles(UUID userId, com.madn.dropzone.dto.request.BinActionRequest request) {
        if (request == null || request.getFileIds() == null || request.getFileIds().isEmpty()) {
            return;
        }
        BinFileDto dto = new BinFileDto();
        dto.setFileIDs(request.getFileIds());
        deleteFiles(userId, dto);
    }

    /**
     * Permanently purges user file metadata and decrements physical storage references.
     * Physical file deletion is delegated entirely to the Garbage Collector.
     */
    private void purgeFilesFromBin(List<StoredFile> storedFileList, StorageAccount account) {
        long totalBytesToRemove = storedFileList.stream()
                .mapToLong(StoredFile::getFileSize)
                .sum();

        // Adjust user quota
        account.setUsedBytes(Math.max(0L, account.getUsedBytes() - totalBytesToRemove));

        for (StoredFile file : storedFileList) {
            // Decrement physical storage object reference count by exactly 1
            if (physicalStorageObjectRepository != null && file.getChecksum() != null) {
                physicalStorageObjectRepository.decrementReferenceCount(file.getChecksum());
            }

            // Permanently remove user-specific metadata record
            fileRepository.delete(file);
        }

        storageAccountRepository.save(account);
    }
}
