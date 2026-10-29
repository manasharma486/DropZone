package com.madn.dropzone.service;

import com.madn.dropzone.dto.response.BreadcrumbItem;
import com.madn.dropzone.dto.response.DriveListingResponse;
import com.madn.dropzone.dto.response.FileResponse;
import com.madn.dropzone.dto.response.FolderResponse;
import com.madn.dropzone.entity.StorageAccount;
import com.madn.dropzone.entity.StoredFile;
import com.madn.dropzone.entity.StoredFolder;
import com.madn.dropzone.enums.FileStatus;
import com.madn.dropzone.exception.common.ResourceNotFoundException;
import com.madn.dropzone.mapper.FileMapper;
import com.madn.dropzone.mapper.FolderMapper;
import com.madn.dropzone.repository.FileRepository;
import com.madn.dropzone.repository.FolderRepository;
import com.madn.dropzone.repository.StorageAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DriveService {

    private final FolderRepository folderRepository;
    private final FileRepository fileRepository;
    private final StorageAccountRepository storageAccountRepository;

    @Transactional(readOnly = true)
    public DriveListingResponse getDriveListing(
            UUID userId,
            UUID folderId,
            String sortBy,
            String order,
            String type,
            int page,
            int size) {

        StorageAccount account = storageAccountRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Storage account not found for user: " + userId));

        UUID currentFolderId = null;
        String currentFolderName = "My Drive";

        if (folderId != null) {
            StoredFolder currentFolder = folderRepository.getStoredFolderByFolderidAndUserId(folderId, userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Folder not found with id: " + folderId));
            currentFolderId = currentFolder.getId();
            currentFolderName = currentFolder.getName();
        }

        // 1. Breadcrumbs
        List<BreadcrumbItem> breadcrumbs = buildBreadcrumbs(userId, folderId);

        // 2. Folders
        List<StoredFolder> folders;
        if (folderId == null) {
            folders = folderRepository.findRootFolders(account.getId());
        } else {
            folders = folderRepository.findChildFolders(account.getId(), folderId);
        }

        List<FolderResponse> folderResponses = folders.stream()
                .filter(f -> f.getStatus() == null || f.getStatus() == FileStatus.ACTIVE)
                .map(FolderMapper::folderToFolderResponse)
                .toList();

        // 3. Files (with pagination and sorting)
        String sortProperty = switch (sortBy != null ? sortBy.toLowerCase().trim() : "name") {
            case "date" -> "uploadedAt";
            case "size" -> "fileSize";
            case "type" -> "contentType";
            default -> "fileName";
        };

        boolean isAsc = order == null || !"desc".equalsIgnoreCase(order.trim());
        Sort sort = isAsc ? Sort.by(sortProperty).ascending() : Sort.by(sortProperty).descending();

        int pageSize = Math.min(200, Math.max(1, size <= 0 ? 50 : size));
        int pageIndex = Math.max(0, page);
        Pageable pageable = PageRequest.of(pageIndex, pageSize, sort);

        Page<StoredFile> filesPage;
        if (folderId == null) {
            filesPage = fileRepository.findRootFiles(account.getId(), FileStatus.ACTIVE, pageable);
        } else {
            filesPage = fileRepository.findFolderFiles(account.getId(), folderId, FileStatus.ACTIVE, pageable);
        }

        List<FileResponse> fileResponses = filesPage.getContent().stream()
                .filter(f -> type == null || type.isBlank() || (f.getContentType() != null && f.getContentType().toLowerCase().contains(type.toLowerCase())))
                .map(FileMapper::fileToFileResponse)
                .toList();

        return DriveListingResponse.builder()
                .currentFolderId(currentFolderId)
                .currentFolderName(currentFolderName)
                .breadcrumbs(breadcrumbs)
                .folders(folderResponses)
                .files(fileResponses)
                .totalFolders(folderResponses.size())
                .totalFiles((int) filesPage.getTotalElements())
                .page(filesPage.getNumber())
                .totalPages(filesPage.getTotalPages())
                .build();
    }

    private List<BreadcrumbItem> buildBreadcrumbs(UUID userId, UUID folderId) {
        if (folderId == null) {
            return List.of(BreadcrumbItem.builder().id(null).name("My Drive").build());
        }

        StoredFolder current = folderRepository.getStoredFolderByFolderidAndUserId(folderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found with id: " + folderId));

        LinkedList<BreadcrumbItem> breadcrumbs = new LinkedList<>();
        int safetyLimit = 0;

        while (current != null) {
            if (++safetyLimit > 100) {
                break;
            }
            breadcrumbs.addFirst(BreadcrumbItem.builder()
                    .id(current.getId())
                    .name(current.getName())
                    .build());
            current = current.getParentFolder();
        }

        breadcrumbs.addFirst(BreadcrumbItem.builder().id(null).name("My Drive").build());
        return breadcrumbs;
    }
}
