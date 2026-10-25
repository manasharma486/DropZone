package com.madn.dropzone.dto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class StoredFolderDto {
    private String folderName;
    private UUID storageAccountId;
    private List<FileFolderDto> fileFolderDtos = new ArrayList<>();
    private UUID parentFolder;
}
