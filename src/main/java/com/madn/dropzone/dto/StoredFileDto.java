package com.madn.dropzone.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.madn.dropzone.enums.FileStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StoredFileDto {

    private UUID fileId;
    private String fileName;
    private String fileSize;
    private Long sizeBytes;
    private String contentType;
    private LocalDateTime uploaded_at;
    private FileStatus status;

    @JsonProperty("id")
    public UUID getId() {
        return fileId;
    }

    public void setId(UUID id) {
        this.fileId = id;
    }

    @JsonProperty("name")
    public String getName() {
        return fileName;
    }

    public void setName(String name) {
        this.fileName = name;
    }

    @JsonProperty("uploadedAt")
    public LocalDateTime getUploadedAt() {
        return uploaded_at;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploaded_at = uploadedAt;
    }
}
