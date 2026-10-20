package com.madn.dropzone.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class StorageService {

    @Value("${spring.storage.path}")
    private Path storageDestination;

    public String saveToStorage(MultipartFile file) throws IOException {
        String storageKey = UUID.randomUUID().toString();
        Files.createDirectories(storageDestination);

        Path destination = storageDestination.resolve(storageKey);
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, destination);
        }

        return storageKey;
    }

    public String calculateChecksum(MultipartFile file) throws Exception {

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (InputStream input = file.getInputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = input.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }

        return HexFormat.of().formatHex(digest.digest());
    }
}
