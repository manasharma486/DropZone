package com.madn.dropzone.controller;

import com.madn.dropzone.dto.response.DriveListingResponse;
import com.madn.dropzone.security.CustomUserDetails;
import com.madn.dropzone.service.DriveService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/drive")
@RequiredArgsConstructor
public class DriveController {

    private final DriveService driveService;

    @GetMapping
    public ResponseEntity<DriveListingResponse> getDrive(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(value = "userId", required = false) UUID fallbackUserId,
            @RequestParam(value = "folderId", required = false) String folderIdStr,
            @RequestParam(value = "sortBy", defaultValue = "name") String sortBy,
            @RequestParam(value = "order", defaultValue = "asc") String order,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {

        UUID effectiveUserId = userDetails != null ? userDetails.getUserId() : fallbackUserId;
        if (effectiveUserId == null) {
            throw new org.springframework.security.access.AccessDeniedException("Authentication required");
        }

        UUID folderId = (folderIdStr != null && !folderIdStr.isBlank() && !"null".equalsIgnoreCase(folderIdStr.trim()) && !"undefined".equalsIgnoreCase(folderIdStr.trim()))
                ? UUID.fromString(folderIdStr.trim())
                : null;

        DriveListingResponse response = driveService.getDriveListing(
                effectiveUserId, folderId, sortBy, order, type, page, size
        );
        return ResponseEntity.ok(response);
    }
}
