package com.madn.dropzone.mapper;

import com.madn.dropzone.dto.StoredFolderDto;
import com.madn.dropzone.entity.StoredFolder;

public class FolderMapper {

    public static StoredFolder dtoToStoredFolder(StoredFolderDto storedFolderDto) {
        if (storedFolderDto == null) {
            return null;
        }
        StoredFolder storedFolder = new StoredFolder();
        storedFolder.setName(storedFolderDto.getFolderName());
        return storedFolder;
    }

    public static StoredFolderDto folderToDto(StoredFolder storedFolder) {
        if (storedFolder == null) {
            return null;
        }
        StoredFolderDto dto = new StoredFolderDto();
        dto.setId(storedFolder.getId());
        dto.setFolderName(storedFolder.getName());
        if (storedFolder.getStorageAccount() != null) {
            dto.setStorageAccountId(storedFolder.getStorageAccount().getId());
        }
        if (storedFolder.getParentFolder() != null) {
            dto.setParentFolder(storedFolder.getParentFolder().getId());
        }
        return dto;
    }

    public static com.madn.dropzone.dto.response.FolderResponse folderToFolderResponse(StoredFolder storedFolder) {
        if (storedFolder == null) {
            return null;
        }
        return com.madn.dropzone.dto.response.FolderResponse.builder()
                .id(storedFolder.getId())
                .name(storedFolder.getName())
                .parentFolderId(storedFolder.getParentFolder() != null ? storedFolder.getParentFolder().getId() : null)
                .createdAt(storedFolder.getCreatedAt())
                .updatedAt(storedFolder.getUpdatedAt())
                .build();
    }
}
