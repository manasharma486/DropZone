package com.madn.dropzone.service;
import com.madn.dropzone.entity.StoredFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class StorageService {

    @Value("${spring.storage.path}")
    private Path storageDestination;

    public String saveToStorage(MultipartFile file) {
        try {
            // Basic validations
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("File cannot be empty");
            }

            if (file.getOriginalFilename() == null ||
                    file.getOriginalFilename().isBlank()) {
                throw new IllegalArgumentException("Invalid filename");
            }

            if (storageDestination == null) {
                throw new IllegalStateException("Storage path is not configured");
            }

            // Ensure storage directory exists
            Files.createDirectories(storageDestination);

            String storageKey = UUID.randomUUID().toString();
            Path destination = storageDestination.resolve(storageKey)
                    .normalize();

            // Ensure destination stays inside storage directory
            if (!destination.startsWith(storageDestination.normalize())) {
                throw new SecurityException("Invalid storage destination");
            }

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destination);
            }

            return storageKey;

        } catch (IllegalArgumentException | IllegalStateException |
                 SecurityException e) {
            throw e;
        } catch (IOException e) {
            throw new RuntimeException("Failed to save file to storage", e);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error while saving file", e);
        }
    }

    public String calculateChecksum(MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException(
                        "Cannot calculate checksum for an empty file");
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            try (InputStream input = file.getInputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;

                while ((bytesRead = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }

            return HexFormat.of().formatHex(digest.digest());

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to calculate file checksum", e);
        } catch (Exception e) {
            throw new RuntimeException("Unexpected error calculating checksum", e);
        }
    }

    public void deleteFromStorage(String storageKey) {
        try {
            Path filePath = storageDestination.resolve(storageKey).normalize();

            if (!filePath.startsWith(storageDestination.normalize())) {
                throw new SecurityException("Invalid storage key");
            }

            Files.deleteIfExists(filePath);

        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file from storage", e);
        }
    }


    public Path getFilePath(StoredFile file) {

        Path filePath = storageDestination
                .resolve(file.getStorageKey())
                .normalize();

        // Prevent path traversal
        if (!filePath.startsWith(storageDestination)) {
            throw new IllegalArgumentException("Invalid storage path");
        }

        // Check whether the physical file exists and is readable
        if (!Files.isRegularFile(filePath)
                || !Files.isReadable(filePath)) {

            throw new IllegalArgumentException(
                    "Physical file does not exist or is unreadable"
            );
        }

        return filePath;
    }

}
