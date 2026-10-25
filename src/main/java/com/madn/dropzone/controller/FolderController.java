package com.madn.dropzone.controller;

import com.madn.dropzone.dto.FileFolderDto;
import com.madn.dropzone.dto.StoredFolderDto;
import com.madn.dropzone.service.FolderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/folders")
public class FolderController {

    private final FolderService folderService;

    public FolderController(FolderService folderService) {
        this.folderService = folderService;
    }

    // Create a new folder
    @PostMapping
    public ResponseEntity<String> createFolder(
            @RequestParam UUID userId,
            @RequestBody StoredFolderDto storedFolderDto) {
        return ResponseEntity.ok(
                folderService.createFolder(userId, storedFolderDto));
    }

    // Get all folders belonging to a user
    @GetMapping
    public ResponseEntity<List<StoredFolderDto>> getAllFolders(
            @RequestParam UUID userId) {
        return ResponseEntity.ok(
                folderService.getAllFolders(userId));
    }

    // Get folder details
    @GetMapping("/{folderId}")
    public ResponseEntity<StoredFolderDto> getFolderById(
            @RequestParam UUID userId,
            @PathVariable UUID folderId) {
        return ResponseEntity.ok(
                folderService.getFolderById(userId, folderId));
    }

    // Rename a folder
    @PatchMapping("/{folderId}")
    public ResponseEntity<String> renameFolder(
            @RequestParam UUID userId,
            @PathVariable UUID folderId,
            @RequestParam("name") String name) {
        return ResponseEntity.ok(
                folderService.renameFolder(
                        userId, folderId, name));
    }

    // Move folder to bin
    @DeleteMapping("/{folderId}")
    public ResponseEntity<String> deleteFolder(
            @RequestParam UUID userId,
            @PathVariable UUID folderId) {
        return ResponseEntity.ok(
                folderService.deleteFolder(userId, folderId));
    }

    // Get files and subfolders inside a folder
    @GetMapping("/{folderId}/contents")
    public ResponseEntity<FileFolderDto> getFolderContents(
            @RequestParam UUID userId,
            @PathVariable UUID folderId) {
        return ResponseEntity.ok(
                folderService.getFolderContents(userId, folderId));
    }

    // Move folder to another parent folder
    @PatchMapping("/{folderId}/move")
    public ResponseEntity<String> moveFolder(
            @RequestParam UUID userId,
            @PathVariable UUID folderId,
            @RequestBody StoredFolderDto storedFolderDto) {
        return ResponseEntity.ok(
                folderService.moveFolder(
                        userId, folderId, storedFolderDto));
    }

    // Copy a folder
    @PostMapping("/{folderId}/copy")
    public ResponseEntity<String> copyFolder(
            @RequestParam UUID userId,
            @PathVariable UUID folderId,
            @RequestBody StoredFolderDto storedFolderDto) {
        return ResponseEntity.ok(
                folderService.copyFolder(
                        userId, folderId, storedFolderDto));
    }

    // Get folder breadcrumb
    @GetMapping("/{folderId}/breadcrumb")
    public ResponseEntity<?> getFolderBreadcrumb(
            @RequestParam UUID userId,
            @PathVariable UUID folderId) {
        return ResponseEntity.ok(
                folderService.getFolderBreadcrumb(userId, folderId));
    }
}
