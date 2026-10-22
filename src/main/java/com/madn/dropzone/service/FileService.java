package com.madn.dropzone.service;
import com.madn.dropzone.entity.StorageAccount;
import com.madn.dropzone.entity.StoredFile;
import com.madn.dropzone.enums.FileStatus;
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

        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                throw new RuntimeException("File cannot be empty");
            }

            if (file.getSize() > account.getQuotaBytes() - account.getUsedBytes()) {
                throw new RuntimeException("Storage quota exceeded");
            }

            String storageKey = storageService.saveToStorage(file);
            String checksum = storageService.calculateChecksum(file);

            StoredFile storedFile = new StoredFile();
            storedFile.setFileName(file.getOriginalFilename());
            storedFile.setFileSize(file.getSize());
            storedFile.setChecksum(checksum);
            storedFile.setStorageKey(storageKey);
            storedFile.setStatus(FileStatus.ACTIVE);
            storedFile.setContentType(file.getContentType());
            storedFile.setFolder(null);
            storedFile.setStorageAccount(account);

            fileRepository.save(storedFile);

            account.setUsedBytes(account.getUsedBytes() + file.getSize());
            storageAccountRepository.save(account);

        }

        return "File saved successfully";
    }

    public String deleteFile(UUID fileid, UUID userid) {
        StoredFile file = fileRepository.getStoredFileById(fileid).orElseThrow(() -> new RuntimeException("File Does not exist"));
        file.setStatus(FileStatus.DELETED);
        fileRepository.save(file);
        return "File Deleated SuccessFully !!";
    }
}
