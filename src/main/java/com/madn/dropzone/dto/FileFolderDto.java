package com.madn.dropzone.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class FileFolderDto {
    private List<StoredFileDto> filesInsideFolderList;
    private List<StoredFolderDto> folderInsideFolderList;
}
