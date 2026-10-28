package com.madn.dropzone.service;

import com.madn.dropzone.dto.BinFileDto;
import com.madn.dropzone.dto.StoredFileDto;
import com.madn.dropzone.entity.StorageAccount;
import com.madn.dropzone.entity.StoredFile;
import com.madn.dropzone.enums.FileStatus;
import com.madn.dropzone.exception.common.DataIntegrityViolationException;
import com.madn.dropzone.exception.common.ResourceNotFoundException;
import com.madn.dropzone.exception.validation.MethodArgumentNotValidException;
import com.madn.dropzone.mapper.FileMapper;
import com.madn.dropzone.repository.FileRepository;
import com.madn.dropzone.repository.StorageAccountRepository;
import com.madn.dropzone.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class BinService {
    private final UserRepository userRepository;
    private final FileRepository fileRepository;
    private final StorageAccountRepository storageAccountRepository;
    private final StorageService storageService;

    public BinService(UserRepository userRepository,
                      FileRepository fileRepository,
                      StorageAccountRepository storageAccountRepository,
                      StorageService storageService) {
        this.userRepository = userRepository;
        this.fileRepository = fileRepository;
        this.storageAccountRepository = storageAccountRepository;
        this.storageService = storageService;
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

        if (fileList.isEmpty()) {
            return "Nothing to restore";
        }

        for (StoredFile file : fileList) {
            file.setStatus(FileStatus.ACTIVE);
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

        // Prevent processing the same file more than once.
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

        deleteFromStorage(fileList, account);

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

        if (fileList.isEmpty()) {
            return "Bin is already empty!";
        }

        deleteFromStorage(fileList, account);

        return "All files deleted successfully!";
    }

    private boolean canWeRemoveItFromStorage(StoredFile file) {

        long count = fileRepository.countFilesWithStatuses(
                file.getChecksum(),
                List.of(FileStatus.DELETED, FileStatus.ACTIVE)
        );

        return count == 0;
    }

    private void deleteFromStorage(
            List<StoredFile> storedFileList,
            StorageAccount account) {

        long totalBytesToRemove = storedFileList.stream()
                .mapToLong(StoredFile::getFileSize)
                .sum();

        if (totalBytesToRemove > account.getUsedBytes()) {
            throw new DataIntegrityViolationException(
                    "Storage usage cannot be negative"
            );
        }

        // Update quota once, after validating the total.
        account.setUsedBytes(account.getUsedBytes() - totalBytesToRemove);

        for (StoredFile file : storedFileList) {
            file.setStatus(FileStatus.TRASHED);
        }

        fileRepository.saveAllAndFlush(storedFileList);

        for (StoredFile file : storedFileList) {

            if (canWeRemoveItFromStorage(file)) {
                storageService.deleteFromStorage(file.getStorageKey());

                fileRepository.removeStoredFileByChecksum(
                        file.getChecksum()
                );
            }
        }

        storageAccountRepository.save(account);
    }
}
