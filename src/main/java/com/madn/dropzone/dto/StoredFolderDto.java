package com.madn.dropzone.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StoredFolderDto {

    private UUID folderId;
    private String folderName;
    private UUID storageAccountId;
    private FileFolderDto fileFolderDto;
    private UUID parentFolder;

    @JsonProperty("id")
    public UUID getId() {
        return folderId;
    }

    public void setId(UUID id) {
        this.folderId = id;
    }

    @JsonProperty("name")
    public String getName() {
        return folderName;
    }

    public void setName(String name) {
        this.folderName = name;
    }

    @JsonProperty("parentFolderId")
    public UUID getParentFolderId() {
        return parentFolder;
    }

    public void setParentFolderId(UUID parentFolderId) {
        this.parentFolder = parentFolderId;
    }
}
