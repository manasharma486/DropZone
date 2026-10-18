package com.madn.dropzone.entity;

import com.madn.dropzone.enums.FileStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "stored_files",
        indexes = {
                @Index(
                        name = "idx_stored_files_account",
                        columnList = "storage_account_id"
                ),
                @Index(
                        name = "idx_stored_files_status",
                        columnList = "status"
                )
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_stored_files_storage_key",
                        columnNames = "storage_key"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class StoredFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Size(max = 255)
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Positive
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @NotBlank
    @Size(max = 150)
    @Column(name = "content_type", nullable = false, length = 150)
    private String contentType;

    @NotBlank
    @Size(max = 255)
    @Column(name = "storage_key", nullable = false, length = 255)
    private String storageKey;

    @Size(max = 64)
    @Column(name = "checksum", length = 64)
    private String checksum;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FileStatus status = FileStatus.ACTIVE;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        uploadedAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}