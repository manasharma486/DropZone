package com.madn.dropzone.controller;

import com.madn.dropzone.dto.FileFolderDto;
import com.madn.dropzone.dto.StoredFolderDto;
import com.madn.dropzone.dto.request.CreateFolderRequest;
import com.madn.dropzone.dto.request.MoveFolderRequest;
import com.madn.dropzone.dto.request.RenameFolderRequest;
import com.madn.dropzone.dto.response.BreadcrumbItem;
import com.madn.dropzone.dto.response.FolderResponse;
import com.madn.dropzone.security.CustomUserDetails;
import com.madn.dropzone.service.FolderArchiveService;
import com.madn.dropzone.service.FolderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/folders")
public class FolderController {

    private final FolderService folderService;
    private final FolderArchiveService folderArchiveService;

    public FolderController(FolderService folderService, FolderArchiveService folderArchiveService) {
        this.folderService = folderService;
        this.folderArchiveService = folderArchiveService;
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

    // Create a new folder
    @PostMapping
    public ResponseEntity<FolderResponse> createFolder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(value = "userId", required = false) UUID fallbackUserId,
            @Valid @RequestBody CreateFolderRequest request) {
        UUID userId = resolveUserId(userDetails, fallbackUserId);
        FolderResponse response = folderService.createFolder(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Get all folders belonging to a user (optionally filtered by status)
    @GetMapping
    public ResponseEntity<List<StoredFolderDto>> getAllFolders(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(value = "userId", required = false) UUID fallbackUserId,
            @RequestParam(value = "status", required = false) com.madn.dropzone.enums.FileStatus status) {
        UUID userId = resolveUserId(userDetails, fallbackUserId);
        return ResponseEntity.ok(status != null ? folderService.getAllFolders(userId, status) : folderService.getAllFolders(userId));
    }

    // Get folder details
    @GetMapping("/{folderId}")
    public ResponseEntity<FolderResponse> getFolderById(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(value = "userId", required = false) UUID fallbackUserId,
            @PathVariable UUID folderId) {
        UUID userId = resolveUserId(userDetails, fallbackUserId);
        return ResponseEntity.ok(folderService.getFolder(userId, folderId));
    }

    // Rename a folder
    @PatchMapping("/{folderId}")
    public ResponseEntity<FolderResponse> renameFolder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(value = "userId", required = false) UUID fallbackUserId,
            @PathVariable UUID folderId,
            @RequestBody(required = false) RenameFolderRequest request,
            @RequestParam(value = "name", required = false) String paramName) {
        UUID userId = resolveUserId(userDetails, fallbackUserId);
        String name = (request != null && request.getName() != null) ? request.getName() : paramName;
        RenameFolderRequest renameRequest = RenameFolderRequest.builder().name(name).build();
        return ResponseEntity.ok(folderService.renameFolder(userId, folderId, renameRequest));
    }

    // Move folder to bin
    @DeleteMapping("/{folderId}")
    public ResponseEntity<Void> deleteFolder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(value = "userId", required = false) UUID fallbackUserId,
            @PathVariable UUID folderId) {
        UUID userId = resolveUserId(userDetails, fallbackUserId);
        folderService.deleteFolder(userId, folderId);
        return ResponseEntity.noContent().build();
    }

    // Restore folder from bin
    @PostMapping("/{folderId}/restore")
    public ResponseEntity<String> restoreFolder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(value = "userId", required = false) UUID fallbackUserId,
            @PathVariable UUID folderId) {
        UUID userId = resolveUserId(userDetails, fallbackUserId);
        return ResponseEntity.ok(folderService.restoreFolder(userId, folderId));
    }

    // Permanently delete folder from bin
    @DeleteMapping("/{folderId}/permanent")
    public ResponseEntity<String> deleteFolderPermanently(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(value = "userId", required = false) UUID fallbackUserId,
            @PathVariable UUID folderId) {
        UUID userId = resolveUserId(userDetails, fallbackUserId);
        return ResponseEntity.ok(folderService.deleteFolderPermanently(userId, folderId));
    }

    // Get files and subfolders inside a folder
    @GetMapping("/{folderId}/contents")
    public ResponseEntity<FileFolderDto> getFolderContents(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(value = "userId", required = false) UUID fallbackUserId,
            @PathVariable UUID folderId) {
        UUID userId = resolveUserId(userDetails, fallbackUserId);
        return ResponseEntity.ok(folderService.getFolderContents(userId, folderId));
    }

    // Move folder to another parent folder
    @PatchMapping("/{folderId}/move")
    public ResponseEntity<FolderResponse> moveFolder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(value = "userId", required = false) UUID fallbackUserId,
            @PathVariable UUID folderId,
            @RequestBody MoveFolderRequest request) {
        UUID userId = resolveUserId(userDetails, fallbackUserId);
        return ResponseEntity.ok(folderService.moveFolder(userId, folderId, request));
    }

    // Copy a folder
    @PostMapping("/{folderId}/copy")
    public ResponseEntity<String> copyFolder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(value = "userId", required = false) UUID fallbackUserId,
            @PathVariable UUID folderId,
            @RequestBody StoredFolderDto storedFolderDto) {
        UUID userId = resolveUserId(userDetails, fallbackUserId);
        return ResponseEntity.ok(folderService.copyFolder(userId, folderId, storedFolderDto));
    }

    // Get folder breadcrumb
    @GetMapping("/{folderId}/breadcrumb")
    public ResponseEntity<List<BreadcrumbItem>> getFolderBreadcrumb(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(value = "userId", required = false) UUID fallbackUserId,
            @PathVariable UUID folderId) {
        UUID userId = resolveUserId(userDetails, fallbackUserId);
        return ResponseEntity.ok(folderService.getBreadcrumbs(userId, folderId));
    }

    // Streaming folder ZIP download
    @GetMapping("/{folderId}/download")
    public ResponseEntity<StreamingResponseBody> downloadFolder(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(value = "userId", required = false) UUID fallbackUserId,
            @PathVariable UUID folderId) {
        UUID userId = resolveUserId(userDetails, fallbackUserId);
        FolderResponse folder = folderService.getFolder(userId, folderId);

        String cleanName = folder.getName().replaceAll("[^a-zA-Z0-9._-]", "_");
        String zipFilename = cleanName.isEmpty() ? "folder.zip" : cleanName + ".zip";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + zipFilename + "\"; filename*=UTF-8''" + UriUtils.encode(zipFilename, StandardCharsets.UTF_8))
                .body(outputStream -> folderArchiveService.streamFolderArchive(userId, folderId, outputStream));
    }
}
