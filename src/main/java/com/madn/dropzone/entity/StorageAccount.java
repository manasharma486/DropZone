package com.madn.dropzone.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "storage_accounts")
@Getter
@Setter
@NoArgsConstructor
public class StorageAccount {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "id", nullable = false)
    private User user;

    @PositiveOrZero
    @Column(name = "used_bytes", nullable = false)
    private Long usedBytes = 0L;

    @PositiveOrZero
    @Column(name = "quota_bytes", nullable = false)
    private Long quotaBytes = 1073741824L; // 1 GiB

    @OneToMany(
            mappedBy = "storageAccount",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE}
    )
    private List<StoredFile> files = new ArrayList<>();

    @OneToMany(
            mappedBy = "storageAccount",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE}
    )
    private List<StoredFolder> folders = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void addFile(StoredFile file) {
        files.add(file);
    }

    public void removeFile(StoredFile file) {
        files.remove(file);
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}