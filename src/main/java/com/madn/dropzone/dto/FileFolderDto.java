package com.madn.dropzone.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FileFolderDto {
    @Builder.Default
    private List<StoredFileDto> filesInsideFolderList = new ArrayList<>();

    @Builder.Default
    private List<StoredFolderDto> folderInsideFolderList = new ArrayList<>();
}
