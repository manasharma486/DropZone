package com.madn.dropzone.service;

import com.madn.dropzone.entity.StoredFile;
import com.madn.dropzone.entity.StoredFolder;
import com.madn.dropzone.enums.FileStatus;
import com.madn.dropzone.exception.common.ResourceNotFoundException;
import com.madn.dropzone.exception.file.QuotaExceededException;
import com.madn.dropzone.exception.storage.StorageException;
import com.madn.dropzone.repository.FileRepository;
import com.madn.dropzone.repository.FolderRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.FilterOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class FolderArchiveService {

    private static final Logger log = LoggerFactory.getLogger(FolderArchiveService.class);
    private static final long MAX_ARCHIVE_BYTES = 2L * 1024 * 1024 * 1024; // 2 GiB
    private static final int MAX_FILE_COUNT = 5000;

    private final FolderRepository folderRepository;
    private final FileRepository fileRepository;
    private final StorageService storageService;

    @com.madn.dropzone.aspect.TrackExecutionTime(thresholdMs = 1000)
    public void streamFolderArchive(UUID userId, UUID rootFolderId, OutputStream responseOutputStream) {
        StoredFolder rootFolder = folderRepository.getStoredFolderByFolderidAndUserId(rootFolderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found with id: " + rootFolderId));

        // Pre-flight check: collect all folder IDs in subtree
        Set<UUID> allFolderIds = new HashSet<>();
        collectAllFolderIds(rootFolder, allFolderIds);

        long totalFiles = fileRepository.countByFolderIdInAndStatus(allFolderIds, FileStatus.ACTIVE);
        long totalBytes = fileRepository.sumSizeByFolderIdInAndStatus(allFolderIds, FileStatus.ACTIVE);

        if (totalFiles > MAX_FILE_COUNT) {
            throw new QuotaExceededException("Archive exceeds maximum allowable file count of " + MAX_FILE_COUNT);
        }
        if (totalBytes > MAX_ARCHIVE_BYTES) {
            throw new QuotaExceededException("Archive exceeds maximum allowable size of 2 GiB");
        }

        // The servlet container owns the response stream. Closing ZipOutputStream
        // must finalize the ZIP without closing the container's stream early.
        OutputStream nonClosingOutput = new FilterOutputStream(responseOutputStream) {
            @Override
            public void close() throws java.io.IOException {
                flush();
            }
        };
        try (ZipOutputStream zipOut = new ZipOutputStream(nonClosingOutput)) {
            zipOut.setLevel(Deflater.DEFAULT_COMPRESSION);

            Set<String> entryPaths = new HashSet<>();
            archiveFolderRecursive(rootFolder, "", zipOut, entryPaths, userId);

            zipOut.finish();
        } catch (Exception e) {
            log.error("Error streaming ZIP archive for folder {}: {}", rootFolderId, e.getMessage(), e);
            throw new StorageException("Archive streaming aborted: " + e.getMessage(), e);
        }
    }

    private void collectAllFolderIds(StoredFolder current, Set<UUID> folderIds) {
        folderIds.add(current.getId());
        List<StoredFolder> children = folderRepository.findChildFolders(current.getStorageAccount().getId(), current.getId());
        for (StoredFolder child : children) {
            collectAllFolderIds(child, folderIds);
        }
    }

    private void archiveFolderRecursive(
            StoredFolder currentFolder, String parentPath,
            ZipOutputStream zipOut, Set<String> entryPaths, UUID userId) throws Exception {

        String sanitizedFolderName = sanitizeZipEntryName(currentFolder.getName());
        String currentPath = parentPath.isEmpty()
                ? sanitizedFolderName
                : parentPath + "/" + sanitizedFolderName;

        // Write directory entry for current folder (preserves empty folders)
        String dirEntryName = currentPath + "/";
        if (entryPaths.add(dirEntryName)) {
            ZipEntry dirEntry = new ZipEntry(dirEntryName);
            zipOut.putNextEntry(dirEntry);
            zipOut.closeEntry();
        }

        // 1. Stream files inside current folder
        List<StoredFile> files = fileRepository.getUserFilesInsideFolder(userId, currentFolder.getId(), FileStatus.ACTIVE);
        byte[] buffer = new byte[32768]; // 32 KB buffer

        if (files != null) {
            for (StoredFile file : files) {
                String cleanFileName = sanitizeZipEntryName(file.getFileName());
                String filePath = currentPath + "/" + cleanFileName;
                filePath = resolveUniqueEntryName(entryPaths, filePath);

                ZipEntry fileEntry = new ZipEntry(filePath);
                fileEntry.setTime(System.currentTimeMillis());
                zipOut.putNextEntry(fileEntry);

                Path physicalPath = storageService.getFilePath(file);
                try (InputStream in = Files.newInputStream(physicalPath)) {
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        zipOut.write(buffer, 0, bytesRead);
                    }
                }
                zipOut.closeEntry();
            }
        }

        // 2. Recurse into child folders
        List<StoredFolder> subfolders = folderRepository.findChildFolders(currentFolder.getStorageAccount().getId(), currentFolder.getId());
        if (subfolders != null) {
            for (StoredFolder sub : subfolders) {
                archiveFolderRecursive(sub, currentPath, zipOut, entryPaths, userId);
            }
        }
    }

    public static String sanitizeZipEntryName(String name) {
        if (name == null || name.isBlank()) {
            return "unnamed_item";
        }
        // Zip-Slip defense: strip path traversal sequences and directory separators
        String clean = name.replace('\\', '/');
        clean = clean.replaceAll("(\\.\\./)+", "");
        clean = clean.replaceAll("^[/\\\\]+", "");
        clean = clean.replaceAll("[\\r\\n]", "");
        clean = clean.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (clean.equals(".") || clean.equals("..") || clean.isBlank()) {
            clean = "folder";
        }
        return clean.trim();
    }

    private String resolveUniqueEntryName(Set<String> entryPaths, String desiredPath) {
        String candidate = desiredPath;
        int counter = 1;
        while (!entryPaths.add(candidate)) {
            int extIdx = desiredPath.lastIndexOf('.');
            if (extIdx > 0) {
                candidate = desiredPath.substring(0, extIdx) + " (" + counter++ + ")" + desiredPath.substring(extIdx);
            } else {
                candidate = desiredPath + " (" + counter++ + ")";
            }
        }
        return candidate;
    }
}
