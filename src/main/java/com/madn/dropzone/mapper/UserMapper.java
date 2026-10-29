package com.madn.dropzone.mapper;

import com.madn.dropzone.dto.response.UserResponse;
import com.madn.dropzone.entity.User;

public class UserMapper {

    public static UserResponse userToUserResponse(User user) {
        if (user == null) {
            return null;
        }

        Long used = null;
        Long quota = null;
        String formattedUsed = null;
        String formattedQuota = null;

        if (user.getAccount() != null) {
            used = user.getAccount().getUsedBytes();
            quota = user.getAccount().getQuotaBytes();
            formattedUsed = FileMapper.formatFileSize(used != null ? used : 0L);
            formattedQuota = FileMapper.formatFileSize(quota != null ? quota : 0L);
        }

        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .age(user.getAge())
                .email(user.getEmail())
                .role(user.getRole())
                .usedBytes(used)
                .quotaBytes(quota)
                .formattedUsed(formattedUsed)
                .formattedQuota(formattedQuota)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
