package com.madn.dropzone.mapper;

import com.madn.dropzone.dto.StoredFileDto;
import com.madn.dropzone.entity.StorageAccount;
import com.madn.dropzone.entity.StoredFile;
import com.madn.dropzone.entity.StoredFolder;
import com.madn.dropzone.enums.FileStatus;
import org.springframework.web.multipart.MultipartFile;

public class FileMapper {
    public static StoredFile multipartFileToStoredFile(MultipartFile file , String checksum , String storageKey , StorageAccount account) {
        StoredFile storedFile = new StoredFile();
        storedFile.setFileName(file.getOriginalFilename());
        storedFile.setFileSize(file.getSize());
        storedFile.setChecksum(checksum);
        storedFile.setStorageKey(storageKey);
        storedFile.setStatus(FileStatus.ACTIVE);
        storedFile.setContentType(file.getContentType());
        storedFile.setFolder(null);
        storedFile.setStorageAccount(account);

        return storedFile;
    }

    public static StoredFileDto fileToFileDto(StoredFile file) {
        StoredFileDto fileDto = new StoredFileDto();

        fileDto.setFileId(file.getId());
        fileDto.setFileName(file.getFileName());
        fileDto.setFileSize((file.getFileSize()/1024) + "MB");
        fileDto.setStatus(file.getStatus());
        fileDto.setContentType(file.getContentType());
        fileDto.setUploaded_at(file.getUploadedAt());

        return fileDto;

    }

    public static StoredFile StoredFileCopy(
            StoredFile file,
            MultipartFile multipartFile,
            StorageAccount account
    ) {
        StoredFile file1 = new StoredFile();

        file1.setStorageAccount(account);
        file1.setFileName(multipartFile.getOriginalFilename());
        file1.setFileSize(multipartFile.getSize());
        file1.setContentType(multipartFile.getContentType());

        // Reuse the existing physical file
        file1.setStorageKey(file.getStorageKey());
        file1.setChecksum(file.getChecksum());

        file1.setFolder(null);

        return file1;
    }

    public static StoredFile StoredFileCopy(StoredFile file , StoredFolder folder)
    {
        StoredFile file1 = new StoredFile();

        file1.setStorageAccount(file.getStorageAccount());
        file1.setFileName(file.getFileName());
        file1.setFileSize(file.getFileSize());
        file1.setContentType(file.getContentType());

        file1.setStorageKey(file.getStorageKey());
        file1.setChecksum(file.getChecksum());

        file1.setFolder(folder);

        return file1;
    }
}
