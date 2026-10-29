package com.madn.dropzone.controller;

import com.madn.dropzone.dto.request.CreateUserRequest;
import com.madn.dropzone.dto.response.UserResponse;
import com.madn.dropzone.security.CustomUserDetails;
import com.madn.dropzone.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @PostMapping("/signup")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = service.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(value = "userId", required = false) UUID fallbackUserId) {
        UUID effectiveUserId = userDetails != null ? userDetails.getUserId() : fallbackUserId;
        if (effectiveUserId == null) {
            throw new AccessDeniedException("Authentication required");
        }
        UserResponse response = service.getUserById(effectiveUserId);
        return ResponseEntity.ok(response);
    }
}
