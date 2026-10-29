package com.madn.dropzone.controller;

import com.madn.dropzone.dto.StoredFileDto;
import com.madn.dropzone.dto.request.MoveFileRequest;
import com.madn.dropzone.dto.request.RenameFileRequest;
import com.madn.dropzone.dto.response.FileResponse;
import com.madn.dropzone.enums.FileStatus;
import com.madn.dropzone.security.CustomUserDetails;
import com.madn.dropzone.service.FileService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    private UUID resolveUserId(CustomUserDetails userDetails, UUID fallbackUserId) {
        if (userDetails != null) {
            return userDetails.getUserId();
        }
        if (fallbackUserId != null) {
            return fallbackUserId;
        }
        throw new org.springframework.security.access.AccessDeniedException("Authentication required");
    }

    // upload
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<FileResponse>> uploadFile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("file") List<MultipartFile> file,
            @RequestParam(value = "userid", required = false) UUID fallbackUserId,
            @RequestParam(value = "folderId", required = false) String folderIdStr,
            @RequestParam(value = "folderid", required = false) String legacyFolderIdStr) {
        UUID userId = resolveUserId(userDetails, fallbackUserId);
        String rawFolderId = (folderIdStr != null && !folderIdStr.isBlank()) ? folderIdStr : legacyFolderIdStr;
        UUID folderId = null;
        if (rawFolderId != null && !rawFolderId.isBlank() && !"null".equalsIgnoreCase(rawFolderId.trim()) && !"undefined".equalsIgnoreCase(rawFolderId.trim())) {
            try {
                folderId = UUID.fromString(rawFolderId.trim());
            } catch (IllegalArgumentException ignored) {}
        }
        List<FileResponse> responses = fileService.uploadFiles(file, userId, folderId);
        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }

    // get drive files all (Deprecated in favor of GET /api/drive)
    @Deprecated
    @GetMapping
    public ResponseEntity<List<StoredFileDto>> getFilesInDrive(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(value = "userid", required = false) UUID fallbackUserId) {
        UUID userId = resolveUserId(userDetails, fallbackUserId);
        List<StoredFileDto> storedFileDtoList = fileService.getDriveFiles(userId);
        return ResponseEntity.ok(storedFileDtoList);
    }

    // get single drive file
    @GetMapping("/{fileid}")
    public ResponseEntity<StoredFileDto> getDriveFile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(value = "userid", required = false) UUID fallbackUserId,
            @PathVariable("fileid") UUID fileid) {
        UUID userId = resolveUserId(userDetails, fallbackUserId);
        StoredFileDto storedFileDto = fileService.getDriveFile(userId, fileid);
        return ResponseEntity.ok(storedFileDto);
    }

    // rename file
    @PatchMapping("/{fileid}")
    public ResponseEntity<String> updateFileName(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(value = "userid", required = false) UUID fallbackUserId,
            @PathVariable("fileid") UUID fileid,
            @RequestBody(required = false) RenameFileRequest request,
            @RequestParam(value = "name", required = false) String paramName) {
        UUID userId = resolveUserId(userDetails, fallbackUserId);
        String name = (request != null && request.getName() != null) ? request.getName() : paramName;
        String s = fileService.updateFileName(userId, fileid, name);
        return ResponseEntity.ok(s);
    }

    // move file from 1 folder to another
    @PatchMapping("/{fileid}/move")
    public ResponseEntity<String> moveFile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(value = "userid", required = false) UUID fallbackUserId,
            @PathVariable("fileid") UUID fileid,
            @RequestBody(required = false) MoveFileRequest request,
            @RequestParam(value = "folderid", required = false) String folderidStr) {
        UUID userId = resolveUserId(userDetails, fallbackUserId);
        UUID destFolderId = null;
        if (request != null && request.getDestinationFolderId() != null) {
            destFolderId = request.getDestinationFolderId();
        } else if (folderidStr != null && !folderidStr.isBlank() && !"null".equalsIgnoreCase(folderidStr.trim()) && !"undefined".equalsIgnoreCase(folderidStr.trim())) {
            try {
                destFolderId = UUID.fromString(folderidStr.trim());
            } catch (IllegalArgumentException ignored) {}
        }
        String s = fileService.moveFile(userId, fileid, destFolderId);
        return ResponseEntity.ok(s);
    }

    // copy file
    @PostMapping("/{fileid}/copy")
    public ResponseEntity<String> copyFile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(value = "userid", required = false) UUID fallbackUserId,
            @PathVariable("fileid") UUID fileid,
            @RequestBody(required = false) MoveFileRequest request,
            @RequestParam(value = "folderid", required = false) String folderidStr) {
        UUID userId = resolveUserId(userDetails, fallbackUserId);
        UUID destFolderId = null;
        if (request != null && request.getDestinationFolderId() != null) {
            destFolderId = request.getDestinationFolderId();
        } else if (folderidStr != null && !folderidStr.isBlank() && !"null".equalsIgnoreCase(folderidStr.trim()) && !"undefined".equalsIgnoreCase(folderidStr.trim())) {
            try {
                destFolderId = UUID.fromString(folderidStr.trim());
            } catch (IllegalArgumentException ignored) {}
        }
        String s = fileService.copyFile(userId, fileid, destFolderId);
        return ResponseEntity.ok(s);
    }

    // download file
    @GetMapping("/{fileid}/download")
    public ResponseEntity<Resource> downloadFile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(value = "userid", required = false) UUID fallbackUserId,
            @PathVariable("fileid") UUID fileid) {
        UUID userId = resolveUserId(userDetails, fallbackUserId);
        return fileService.downloadFile(userId, fileid);
    }

    // Search files by filename
    @GetMapping("/search")
    public ResponseEntity<List<StoredFileDto>> searchFiles(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(value = "userid", required = false) UUID fallbackUserId,
            @RequestParam("q") String query,
            @RequestParam(value = "status", required = false) FileStatus status) {
        UUID userId = resolveUserId(userDetails, fallbackUserId);
        return ResponseEntity.ok(fileService.searchFiles(userId, query, status));
    }

    // Sort drive files by name (Deprecated in favor of GET /api/drive)
    @Deprecated
    @GetMapping(params = "sortBy")
    public ResponseEntity<List<StoredFileDto>> sortFiles(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(value = "userid", required = false) UUID fallbackUserId,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String order,
            @RequestParam(value = "status", required = false) FileStatus status) {
        UUID userId = resolveUserId(userDetails, fallbackUserId);
        return ResponseEntity.ok(fileService.sortDriveFiles(userId, sortBy, order, status));
    }

    // Get files inside a specific folder (Deprecated in favor of GET /api/drive)
    @Deprecated
    @GetMapping(params = "folderId")
    public ResponseEntity<List<StoredFileDto>> getFilesByFolder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(value = "userid", required = false) UUID fallbackUserId,
            @RequestParam("folderId") UUID folderId,
            @RequestParam(value = "status", required = false) FileStatus status) {
        UUID userId = resolveUserId(userDetails, fallbackUserId);
        return ResponseEntity.ok(fileService.getFilesByFolder(userId, folderId, status));
    }

    // Filter files by type (Deprecated in favor of GET /api/drive)
    @Deprecated
    @GetMapping(params = "type")
    public ResponseEntity<List<StoredFileDto>> getFilesByType(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(value = "userid", required = false) UUID fallbackUserId,
            @RequestParam("type") String type,
            @RequestParam(value = "status", required = false) FileStatus status) {
        UUID userId = resolveUserId(userDetails, fallbackUserId);
        return ResponseEntity.ok(fileService.getFilesByType(userId, type, status));
    }

    // RESTful delete by path variable
    @DeleteMapping("/{fileid}")
    public ResponseEntity<Void> deleteFileById(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(value = "userid", required = false) UUID fallbackUserId,
            @PathVariable("fileid") UUID fileid) {
        UUID userId = resolveUserId(userDetails, fallbackUserId);
        fileService.deleteFile(fileid, userId);
        return ResponseEntity.noContent().build();
    }

    // Legacy delete by query params
    @DeleteMapping
    public ResponseEntity<String> deleteFile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("fileid") UUID fileid,
            @RequestParam(value = "userid", required = false) UUID fallbackUserId) {
        UUID userId = resolveUserId(userDetails, fallbackUserId);
        String s = fileService.deleteFile(fileid, userId);
        return ResponseEntity.ok(s);
    }
}
