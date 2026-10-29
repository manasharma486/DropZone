package com.madn.dropzone.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriveListingResponse {
    private UUID currentFolderId;
    private String currentFolderName;
    private List<BreadcrumbItem> breadcrumbs;
    private List<FolderResponse> folders;
    private List<FileResponse> files;
    private int totalFolders;
    private int totalFiles;
    private int page;
    private int totalPages;
}
