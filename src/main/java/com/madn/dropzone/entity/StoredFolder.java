package com.madn.dropzone.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "stored_folders")
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
}