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
        fileDto.setFileSize((file.getFileSize() / 1024) + "MB");
        fileDto.setSizeBytes(file.getFileSize());
        fileDto.setStatus(file.getStatus());
        fileDto.setContentType(file.getContentType());
        fileDto.setUploaded_at(file.getUploadedAt());

        return fileDto;
    }

    public static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format(java.util.Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    public static com.madn.dropzone.dto.response.FileResponse fileToFileResponse(StoredFile file) {
        if (file == null) {
            return null;
        }
        long size = file.getFileSize() != null ? file.getFileSize() : 0L;
        return com.madn.dropzone.dto.response.FileResponse.builder()
                .id(file.getId())
                .name(file.getFileName())
                .sizeBytes(size)
                .formattedSize(formatFileSize(size))
                .contentType(file.getContentType())
                .checksum(file.getChecksum())
                .folderId(file.getFolder() != null ? file.getFolder().getId() : null)
                .status(file.getStatus())
                .uploadedAt(file.getUploadedAt())
                .updatedAt(file.getUpdatedAt())
                .build();
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
