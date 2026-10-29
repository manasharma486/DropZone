package com.madn.dropzone.entity;

import com.madn.dropzone.enums.FileStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "stored_folders",
        indexes = {
                @Index(name = "idx_stored_folders_account", columnList = "storage_account_id"),
                @Index(name = "idx_stored_folders_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class StoredFolder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "storage_account_id", nullable = false)
    private StorageAccount storageAccount;

    @OneToMany(mappedBy = "folder")
    private List<StoredFile> fileList = new ArrayList<>();

    @OneToMany(mappedBy = "parentFolder")
    private List<StoredFolder> childFolders = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_folder_id")
    private StoredFolder parentFolder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FileStatus status = FileStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private java.time.LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private java.time.LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = FileStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = java.time.LocalDateTime.now();
    }
}
