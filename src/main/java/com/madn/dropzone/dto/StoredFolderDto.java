package com.madn.dropzone.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class StoredFolderDto {
    private UUID id;
    private String folderName;
    private UUID storageAccountId;
    private FileFolderDto fileFolderDto;
    private UUID parentFolder;

    public UUID getFolderId() {
        return this.id;
    }

    public void setFolderId(UUID folderId) {
        this.id = folderId;
    }

    public String getName() {
        return this.folderName;
    }

    public void setName(String name) {
        this.folderName = name;
    }

    public UUID getParentFolderId() {
        return this.parentFolder;
    }

    public void setParentFolderId(UUID parentFolderId) {
        this.parentFolder = parentFolderId;
    }
}
