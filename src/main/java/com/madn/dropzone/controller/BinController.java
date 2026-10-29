package com.madn.dropzone.controller;

import com.madn.dropzone.dto.BinFileDto;
import com.madn.dropzone.dto.StoredFileDto;
import com.madn.dropzone.dto.request.BinActionRequest;
import com.madn.dropzone.security.CustomUserDetails;
import com.madn.dropzone.service.BinService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bin")
public class BinController {

    private final BinService binService;

    public BinController(BinService binService) {
        this.binService = binService;
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

    @GetMapping
    public ResponseEntity<List<StoredFileDto>> getFilesInBin(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(value = "userid", required = false) UUID fallbackUserId,
            @RequestParam(value = "type", required = false, defaultValue = "") String type) {

        UUID userId = resolveUserId(userDetails, fallbackUserId);
        List<StoredFileDto> storedFileDtoList = binService.getFilesInBin(userId, type);
        return ResponseEntity.ok(storedFileDtoList);
    }

    @PostMapping("/restore")
    public ResponseEntity<String> restoreFiles(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(value = "userid", required = false) UUID fallbackUserId,
            @RequestBody(required = false) BinActionRequest request) {

        UUID userId = resolveUserId(userDetails, fallbackUserId);
        BinFileDto dto = new BinFileDto();
        if (request != null && request.getFileIds() != null) {
            dto.setFileIDs(request.getFileIds());
        }
        String s = binService.restoreFiles(userId, dto);
        return ResponseEntity.ok(s);
    }

    @PostMapping("/restore-all")
    public ResponseEntity<String> restoreAllFiles(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(value = "userid", required = false) UUID fallbackUserId) {

        UUID userId = resolveUserId(userDetails, fallbackUserId);
        String s = binService.restoreAllFiles(userId);
        return ResponseEntity.ok(s);
    }

    @DeleteMapping("/empty")
    public ResponseEntity<String> deleteFiles(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(value = "userid", required = false) UUID fallbackUserId,
            @RequestBody(required = false) BinActionRequest request) {

        UUID userId = resolveUserId(userDetails, fallbackUserId);
        BinFileDto dto = new BinFileDto();
        if (request != null && request.getFileIds() != null) {
            dto.setFileIDs(request.getFileIds());
        }
        String s = binService.deleteFiles(userId, dto);
        return ResponseEntity.ok(s);
    }

    @DeleteMapping("/empty-all")
    public ResponseEntity<String> deleteAllFiles(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(value = "userid", required = false) UUID fallbackUserId) {

        UUID userId = resolveUserId(userDetails, fallbackUserId);
        String s = binService.deleteAllFiles(userId);
        return ResponseEntity.ok(s);
    }
}
