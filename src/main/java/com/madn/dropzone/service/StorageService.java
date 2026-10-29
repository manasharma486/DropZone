package com.madn.dropzone.service;

import com.madn.dropzone.entity.StoredFile;
import com.madn.dropzone.exception.file.EmptyFileException;
import com.madn.dropzone.exception.storage.StorageException;
import com.madn.dropzone.exception.storage.StorageFileNotFoundException;
import com.madn.dropzone.exception.validation.MethodArgumentNotValidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            "exe", "bat", "cmd", "sh", "bash", "bin", "msi", "vbs", "vbe",
            "ps1", "scr", "com", "pif", "dll", "so", "dylib", "jar", "jsp",
            "asp", "aspx", "php"
    );

    @Value("${spring.storage.path}")
    private Path storageDestination;

    public Path getStorageDestination() {
        return storageDestination;
    }

    public Path getStagingDestination() {
        return storageDestination.resolve("staging").normalize();
    }

    private void ensureDirectories() throws IOException {
        if (storageDestination == null) {
            throw new StorageException("Storage path is not configured");
        }
        Files.createDirectories(storageDestination);
        Files.createDirectories(getStagingDestination());
    }

    /**
     * Step 2: Streams uploaded file into isolated temporary staging directory
     */
    public Path stageFile(MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                throw new EmptyFileException("File cannot be empty");
            }

            ensureDirectories();

            Path stagingDir = getStagingDestination();
            String stagingKey = "stage_" + UUID.randomUUID() + ".tmp";
            Path stagedPath = stagingDir.resolve(stagingKey).normalize();

            if (!stagedPath.startsWith(stagingDir)) {
                throw new StorageException("Invalid staging destination path");
            }

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, stagedPath, StandardCopyOption.REPLACE_EXISTING);
            }

            return stagedPath;
        } catch (EmptyFileException e) {
            throw e;
        } catch (IOException e) {
            throw new StorageException("Failed to stream file into staging storage", e);
        }
    }

    /**
     * Step 3: Validates staged file content, magic bytes, and checks for dangerous executables/scripts
     */
    public void validateStagedFile(Path stagedPath, String originalFilename) {
        try {
            if (stagedPath == null || !Files.exists(stagedPath) || !Files.isRegularFile(stagedPath)) {
                throw new StorageException("Staged file does not exist or is not a regular file");
            }

            if (Files.size(stagedPath) == 0) {
                cleanupStagedFile(stagedPath);
                throw new EmptyFileException("File cannot be empty");
            }

            // Filename traversal & length validation
            if (originalFilename == null || originalFilename.isBlank()) {
                cleanupStagedFile(stagedPath);
                throw new MethodArgumentNotValidException("Invalid filename");
            }

            if (originalFilename.contains("\0") || originalFilename.contains("..") ||
                originalFilename.contains("/") || originalFilename.contains("\\")) {
                cleanupStagedFile(stagedPath);
                throw new MethodArgumentNotValidException("Invalid filename characters");
            }

            int lastDotIndex = originalFilename.lastIndexOf('.');
            if (lastDotIndex != -1 && lastDotIndex < originalFilename.length() - 1) {
                String ext = originalFilename.substring(lastDotIndex + 1).toLowerCase();
                if (BLOCKED_EXTENSIONS.contains(ext)) {
                    cleanupStagedFile(stagedPath);
                    throw new MethodArgumentNotValidException("File type not allowed: " + ext);
                }
            }

            // Inspect magic bytes for active executables/scripts
            byte[] header = new byte[16];
            int readBytes;
            try (InputStream in = Files.newInputStream(stagedPath)) {
                readBytes = in.read(header);
            }

            if (readBytes >= 2) {
                // Windows PE Executable/DLL (MZ)
                if (header[0] == 0x4D && header[1] == 0x5A) {
                    cleanupStagedFile(stagedPath);
                    throw new MethodArgumentNotValidException("Executable binary (PE) files are not allowed");
                }
                // Shell Script Shebang (#!)
                if (header[0] == 0x23 && header[1] == 0x21) {
                    cleanupStagedFile(stagedPath);
                    throw new MethodArgumentNotValidException("Executable script files are not allowed");
                }
            }

            if (readBytes >= 4) {
                // Linux ELF binary (\x7fELF)
                if (header[0] == 0x7F && header[1] == 0x45 && header[2] == 0x4C && header[3] == 0x46) {
                    cleanupStagedFile(stagedPath);
                    throw new MethodArgumentNotValidException("Executable binary (ELF) files are not allowed");
                }
                // Mach-O binaries
                if ((header[0] == (byte) 0xFE && header[1] == (byte) 0xED && header[2] == (byte) 0xFA && (header[3] == (byte) 0xCE || header[3] == (byte) 0xCF)) ||
                    (header[0] == (byte) 0xCF && header[1] == (byte) 0xFA && header[2] == (byte) 0xED && header[3] == (byte) 0xFE) ||
                    (header[0] == (byte) 0xCA && header[1] == (byte) 0xFE && header[2] == (byte) 0xBA && header[3] == (byte) 0xBE)) {
                    cleanupStagedFile(stagedPath);
                    throw new MethodArgumentNotValidException("Executable binary files are not allowed");
                }
            }

        } catch (EmptyFileException | MethodArgumentNotValidException e) {
            throw e;
        } catch (IOException e) {
            cleanupStagedFile(stagedPath);
            throw new StorageException("Failed to validate staged file", e);
        }
    }

    /**
     * Step 4: Compute cryptographic checksum (SHA-256) by streaming the staged file
     */
    public String calculateChecksum(Path stagedPath) {
        try {
            if (stagedPath == null || !Files.exists(stagedPath)) {
                throw new StorageException("Cannot calculate checksum: staged file not found");
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(stagedPath)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new StorageException("Failed to calculate file checksum", e);
        }
    }

    /**
     * Step 6: Atomic commit from staging to permanent storage
     */
    public String commitStagedFile(Path stagedPath) {
        try {
            ensureDirectories();

            String storageKey = UUID.randomUUID().toString();
            Path destination = storageDestination.resolve(storageKey).normalize();

            if (!destination.startsWith(storageDestination.normalize())) {
                throw new StorageException("Invalid storage destination");
            }

            try {
                // Atomic rename/move within the same filesystem
                Files.move(stagedPath, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException | AccessDeniedException e) {
                // Fallback to standard move / copy-delete across filesystem boundaries
                Files.move(stagedPath, destination, StandardCopyOption.REPLACE_EXISTING);
            }

            return storageKey;
        } catch (IOException e) {
            throw new StorageException("Failed to commit staged file to permanent storage", e);
        }
    }

    /**
     * Cleans up temporary staged file safely
     */
    public void cleanupStagedFile(Path stagedPath) {
        if (stagedPath != null) {
            try {
                Files.deleteIfExists(stagedPath);
            } catch (Exception e) {
                log.warn("Failed to delete staged temporary file: {}", stagedPath, e);
            }
        }
    }

    /**
     * Scheduled / recovery cleanup for abandoned staging files older than maxAge
     */
    public int cleanOrphanedStagingFiles(Duration maxAge) {
        Path stagingDir = getStagingDestination();
        if (!Files.exists(stagingDir)) {
            return 0;
        }

        Instant cutoff = Instant.now().minus(maxAge);
        int cleanedCount = 0;

        try (Stream<Path> stream = Files.list(stagingDir)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                    if (attrs.lastModifiedTime().toInstant().isBefore(cutoff)) {
                        Files.deleteIfExists(file);
                        cleanedCount++;
                        log.info("Cleaned orphaned staging file: {}", file.getFileName());
                    }
                } catch (Exception e) {
                    log.warn("Failed to clean staging file: {}", file, e);
                }
            }
        } catch (IOException e) {
            log.error("Failed to list staging directory during orphan cleanup", e);
        }

        return cleanedCount;
    }

    /**
     * Save to storage (integrated staging pipeline) - backward compatible
     */
    public String saveToStorage(MultipartFile file) {
        Path staged = stageFile(file);
        try {
            validateStagedFile(staged, file.getOriginalFilename());
            return commitStagedFile(staged);
        } catch (Exception e) {
            cleanupStagedFile(staged);
            throw e;
        }
    }

    public String calculateChecksum(MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                throw new EmptyFileException("Cannot calculate checksum for an empty file");
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
        } catch (EmptyFileException e) {
            throw e;
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new StorageException("Failed to calculate file checksum", e);
        }
    }

    public void deleteFromStorage(String storageKey) {
        try {
            Path filePath = storageDestination.resolve(storageKey).normalize();

            if (!filePath.startsWith(storageDestination.normalize())) {
                throw new StorageException("Invalid storage key");
            }

            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new StorageException("Failed to delete file from storage", e);
        }
    }

    public Path getFilePath(StoredFile file) {
        Path filePath = storageDestination
                .resolve(file.getStorageKey())
                .normalize();

        // Prevent path traversal
        if (!filePath.startsWith(storageDestination.normalize())) {
            throw new StorageException("Invalid storage path");
        }

        // Check whether the physical file exists and is readable
        if (!Files.isRegularFile(filePath) || !Files.isReadable(filePath)) {
            throw new StorageFileNotFoundException(
                    "Physical file does not exist or is unreadable"
            );
        }

        return filePath;
    }
}
