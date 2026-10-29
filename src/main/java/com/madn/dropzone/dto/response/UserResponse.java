package com.madn.dropzone.dto.response;

import com.madn.dropzone.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private UUID id;
    private String name;
    private Integer age;
    private String email;
    private UserRole role;
    private Long usedBytes;
    private Long quotaBytes;
    private String formattedUsed;
    private String formattedQuota;
    private LocalDateTime createdAt;
}
