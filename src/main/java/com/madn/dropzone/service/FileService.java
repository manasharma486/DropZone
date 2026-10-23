package com.madn.dropzone.service;
import com.madn.dropzone.entity.StorageAccount;
import com.madn.dropzone.entity.StoredFile;
import com.madn.dropzone.enums.FileStatus;
import com.madn.dropzone.mapper.FileMapper;
import com.madn.dropzone.repository.FileRepository;
import com.madn.dropzone.repository.StorageAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class FileService {
    private StorageService storageService;
    private FileRepository fileRepository;
    private StorageAccountRepository storageAccountRepository;


    public FileService(StorageService storageService , FileRepository fileRepository , StorageAccountRepository storageAccountRepository) {
        this.fileRepository = fileRepository;
        this.storageService = storageService;
        this.storageAccountRepository = storageAccountRepository;
    }

    @Transactional(rollbackFor = Exception.class)
    public String uploadFile(List<MultipartFile> files, UUID userID) {

        StorageAccount account = storageAccountRepository
                .findByUserId(userID)
                .orElseThrow(() ->
                        new RuntimeException("Storage account not found"));

        long additionalBytes = 0;

        for (MultipartFile file : files) {

            if (file.isEmpty()) {
                throw new RuntimeException("File cannot be empty");
            }

            additionalBytes += file.getSize();

            if (additionalBytes > account.getQuotaBytes() - account.getUsedBytes()) {
                throw new RuntimeException("Storage quota exceeded");
            }

            String checksum = storageService.calculateChecksum(file);

            Optional<StoredFile> fileOptional =
                    fileRepository.existFileInAccount(checksum, userID);

            if (fileOptional.isPresent()) {

                StoredFile existingFile = fileOptional.get();

                if (existingFile.getStatus() == FileStatus.DELETED) {
                    throw new RuntimeException(
                            "File cannot be added; it is already present in the bin!"
                    );
                }

                throw new RuntimeException(
                        "File cannot be added; it is already present in the drive"
                );
            }

            List<StoredFile> matchingFiles =
                    fileRepository.existFileInStorage(checksum);

            StoredFile savedFile;

            if (matchingFiles.isEmpty()) {

                String storageKey = storageService.saveToStorage(file);

                savedFile = FileMapper.multipartFileToStoredFile(
                        file, checksum, storageKey, account
                );

            } else {

                StoredFile existingStorageFile = matchingFiles.getFirst();

                savedFile = FileMapper.StoredFileCopy(
                        existingStorageFile, file, account
                );
            }

            fileRepository.save(savedFile);
            account.setUsedBytes(account.getUsedBytes() + file.getSize());
        }

        storageAccountRepository.save(account);

        return "Files saved successfully";
    }

    public String deleteFile(UUID fileid, UUID userid) {
        StoredFile file = fileRepository.getStoredFileByIdAndUserId(userid,fileid).orElseThrow(() -> new RuntimeException("File Does not exist"));
        file.setStatus(FileStatus.DELETED);
        fileRepository.save(file);
        return "File Deleated SuccessFully !!";
    }
}
