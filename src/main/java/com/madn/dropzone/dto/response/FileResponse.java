package com.madn.dropzone.dto.response;

import com.madn.dropzone.enums.FileStatus;
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
public class FileResponse {
    private UUID id;
    private String name;
    private long sizeBytes;
    private String formattedSize; // e.g. "2.4 MB"
    private String contentType;
    private String checksum;
    private UUID folderId;
    private FileStatus status;
    private LocalDateTime uploadedAt;
    private LocalDateTime updatedAt;
}
