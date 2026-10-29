package com.madn.dropzone.entity;

import com.madn.dropzone.enums.StorageObjectStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "physical_storage_objects",
        indexes = {
                @Index(name = "idx_pso_status", columnList = "status"),
                @Index(name = "idx_pso_ref_count", columnList = "reference_count")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhysicalStorageObject {

    @Id
    @Column(name = "checksum", nullable = false, length = 64)
    @NotBlank
    private String checksum;

    @Column(name = "storage_key", nullable = false, length = 255)
    @NotBlank
    private String storageKey;

    @Column(name = "physical_size", nullable = false)
    @NotNull
    private Long physicalSize;

    @Column(name = "reference_count", nullable = false)
    @Builder.Default
    private int referenceCount = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private StorageObjectStatus status = StorageObjectStatus.ACTIVE;

    @Column(name = "gc_eligible_at")
    private LocalDateTime gcEligibleAt;

    @Column(name = "failure_count", nullable = false)
    @Builder.Default
    private int failureCount = 0;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_verified_at")
    private LocalDateTime lastVerifiedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (lastVerifiedAt == null) {
            lastVerifiedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = StorageObjectStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        lastVerifiedAt = LocalDateTime.now();
    }
}
