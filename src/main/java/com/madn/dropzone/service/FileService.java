package com.madn.dropzone.service;

import com.madn.dropzone.dto.StoredFileDto;
import com.madn.dropzone.entity.StorageAccount;
import com.madn.dropzone.entity.StoredFile;
import com.madn.dropzone.entity.StoredFolder;
import com.madn.dropzone.enums.FileStatus;
import com.madn.dropzone.exception.common.DuplicateResourceException;
import com.madn.dropzone.exception.common.ResourceNotFoundException;
import com.madn.dropzone.exception.file.EmptyFileException;
import com.madn.dropzone.exception.file.QuotaExceededException;
import com.madn.dropzone.exception.folder.InvalidParentException;
import com.madn.dropzone.exception.storage.StorageException;
import com.madn.dropzone.exception.validation.MethodArgumentNotValidException;
import com.madn.dropzone.mapper.FileMapper;
import com.madn.dropzone.repository.FileRepository;
import com.madn.dropzone.repository.FolderRepository;
import com.madn.dropzone.repository.StorageAccountRepository;
import com.madn.dropzone.repository.UserRepository;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.madn.dropzone.mapper.FileMapper.fileToFileDto;

@Service
public class FileService {
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final FileRepository fileRepository;
    private final StorageAccountRepository storageAccountRepository;
    private final FolderRepository folderRepository;

    public FileService(StorageService storageService,
                       FileRepository fileRepository,
                       StorageAccountRepository storageAccountRepository,
                       FolderRepository folderRepository,
                       UserRepository userRepository) {
        this.fileRepository = fileRepository;
        this.storageService = storageService;
        this.storageAccountRepository = storageAccountRepository;
        this.folderRepository = folderRepository;
        this.userRepository = userRepository;
    }

    @Transactional(rollbackFor = Exception.class)
    public String uploadFile(List<MultipartFile> files, UUID userID) {

        StorageAccount account = storageAccountRepository
                .findByUserId(userID)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Storage account not found for user: " + userID));

        long additionalBytes = 0;

        for (MultipartFile file : files) {

            if (file.isEmpty()) {
                throw new EmptyFileException("File cannot be empty");
            }

            additionalBytes += file.getSize();

            if (additionalBytes > account.getQuotaBytes() - account.getUsedBytes()) {
                throw new QuotaExceededException("Storage quota exceeded");
            }

            String checksum = storageService.calculateChecksum(file);

            Optional<StoredFile> fileOptional =
                    fileRepository.existFileInAccount(checksum, userID);

            if (fileOptional.isPresent()) {

                StoredFile existingFile = fileOptional.get();

                if (existingFile.getStatus() == FileStatus.DELETED) {
                    throw new DuplicateResourceException(
                            "File cannot be added; it is already present in the bin!"
                    );
                }

                throw new DuplicateResourceException(
                        "File cannot be added; it is already present in the drive"
                );
            }

            List<StoredFile> matchingFiles =
                    fileRepository.existFileInStorage(checksum);

            StoredFile savedFile;

            if (matchingFiles.isEmpty()) {

                String storageKey = storageService.saveToStorage(file);

                savedFile = FileMapper.multipartFileToStoredFile(
                        file, checksum, storageKey, account
                );

            } else {

                StoredFile existingStorageFile = matchingFiles.getFirst();

                savedFile = FileMapper.StoredFileCopy(
                        existingStorageFile, file, account
                );
            }

            fileRepository.save(savedFile);
            account.setUsedBytes(account.getUsedBytes() + file.getSize());
        }

        storageAccountRepository.save(account);

        return "Files saved successfully";
    }

    public String deleteFile(UUID fileid, UUID userid) {
        StoredFile file = fileRepository.getStoredFileByIdAndUserId(userid, fileid)
                .orElseThrow(() -> new ResourceNotFoundException("File Does not exist with id: " + fileid));
        file.setStatus(FileStatus.DELETED);
        fileRepository.save(file);
        return "File Deleated SuccessFully !!";
    }

    public List<StoredFileDto> getDriveFiles(UUID userid) {
        boolean check = storageAccountRepository.existsById(userid);
        if (check) {
            return fileRepository.getStoredFileFromStatus(userid, FileStatus.ACTIVE, "")
                    .stream()
                    .map(FileMapper::fileToFileDto)
                    .toList();
        } else {
            throw new ResourceNotFoundException("User Does not Exists !!");
        }
    }

    public StoredFileDto getDriveFile(UUID userid, UUID fileid) {
        StoredFile storedFile = fileRepository.getStoredFileByIdAndUserId(userid, fileid)
                .orElseThrow(() -> new ResourceNotFoundException("File does not exists with id: " + fileid));

        return FileMapper.fileToFileDto(storedFile);
    }

    public String updateFileName(UUID userid, UUID fileid, String name) {

        if (name == null || name.isBlank()) {
            throw new MethodArgumentNotValidException("Filename cannot be empty");
        }

        name = name.trim();

        if (name.length() > 255) {
            throw new MethodArgumentNotValidException("Filename cannot exceed 255 characters");
        }

        if (name.equals(".") || name.equals("..")) {
            throw new MethodArgumentNotValidException("Invalid filename");
        }

        if (name.matches(".*[\\\\/:*?\"<>|].*")) {
            throw new MethodArgumentNotValidException(
                    "Filename cannot contain these characters: \\ / : * ? \" < > |"
            );
        }

        if (name.endsWith(".") || name.endsWith(" ")) {
            throw new MethodArgumentNotValidException(
                    "Filename cannot end with a dot or space"
            );
        }

        StoredFile storedFile = fileRepository.getStoredFileByIdAndUserId(userid, fileid)
                .orElseThrow(() -> new ResourceNotFoundException("No File Exist with id: " + fileid));

        storedFile.setFileName(name);
        fileRepository.save(storedFile);

        return "File Name changed to: " + name;
    }

    @Transactional
    public String moveFile(UUID userId, UUID fileId, UUID folderId) {

        StoredFile storedFile = fileRepository
                .getStoredFileByIdAndUserId(userId, fileId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("File does not exist or access is denied with id: " + fileId));

        if (folderId == null) {
            storedFile.setFolder(null);
        } else {
            StoredFolder folder = folderRepository
                    .getStoredFolderByFolderidAndUserId(folderId, userId)
                    .orElseThrow(() ->
                            new InvalidParentException("Destination folder does not exist: " + folderId));

            storedFile.setFolder(folder);
        }

        fileRepository.save(storedFile);

        return "File moved successfully!";
    }

    public String copyFile(UUID userid, UUID fileid, UUID folderid) {

        StorageAccount account = storageAccountRepository.findByUserId(userid)
                .orElseThrow(() -> new ResourceNotFoundException("User Does not exist: " + userid));

        StoredFile storedFile = fileRepository
                .getStoredFileByIdAndUserId(userid, fileid)
                .orElseThrow(() ->
                        new ResourceNotFoundException("File does not exist or access is denied with id: " + fileid));

        StoredFile copyFile = FileMapper.StoredFileCopy(storedFile, null);
        account.storeFileInAccount(copyFile);

        if (folderid != null) {
            StoredFolder folder = folderRepository
                    .getStoredFolderByFolderidAndUserId(folderid, userid)
                    .orElseThrow(() ->
                            new InvalidParentException("Destination folder does not exist: " + folderid));

            copyFile.setFolder(folder);
        }

        fileRepository.save(copyFile);
        storageAccountRepository.save(account);

        return "Copying Successfull !!";
    }

    public ResponseEntity<Resource> downloadFile(UUID userid, UUID fileid) {

        StoredFile storedFile = fileRepository
                .getStoredFileByIdAndUserId(userid, fileid)
                .orElseThrow(() ->
                        new ResourceNotFoundException("File does not exist or access is denied with id: " + fileid));

        try {
            Path filePath = storageService.getFilePath(storedFile);
            Resource resource = new UrlResource(filePath.toUri());

            String contentType = storedFile.getContentType();

            if (contentType == null || contentType.isBlank()) {
                contentType = Files.probeContentType(filePath);
            }

            if (contentType == null || contentType.isBlank()) {
                contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(Files.size(filePath))
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\""
                                    + storedFile.getFileName()
                                    .replace("\"", "")
                                    + "\""
                    )
                    .body(resource);

        } catch (IOException e) {
            throw new StorageException("Unable to download the file !! ", e);
        }
    }

    public List<StoredFileDto> searchFiles(UUID userId, String query, FileStatus status) {

        if (status == null) {
            status = FileStatus.ACTIVE;
        } else if (status == FileStatus.TRASHED) {
            throw new MethodArgumentNotValidException("Searching trashed files is Not Allowed");
        }

        if (userId == null || !userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("Invalid User ID: " + userId);
        }

        if (query == null || query.isBlank()) {
            throw new MethodArgumentNotValidException("Search query cannot be empty");
        }

        return fileRepository
                .getStoredFilesByFileNameAndAccount(
                        query.trim(), userId, status
                )
                .stream()
                .map(FileMapper::fileToFileDto)
                .toList();
    }

    public List<StoredFileDto> sortDriveFiles(
            UUID userId,
            String sortBy,
            String order,
            FileStatus status
    ) {
        if (userId == null || !userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("Invalid User ID: " + userId);
        }

        if (sortBy == null || order == null) {
            throw new MethodArgumentNotValidException(
                    "Sort field and order are required"
            );
        }

        if (status == null) {
            status = FileStatus.ACTIVE;
        } else if (status == FileStatus.TRASHED) {
            throw new MethodArgumentNotValidException("Sorting trashed files is Not Allowed");
        }

        sortBy = sortBy.trim().toLowerCase();
        order = order.trim().toLowerCase();

        if (!order.equals("asc") && !order.equals("desc")) {
            throw new MethodArgumentNotValidException("Invalid order type");
        }

        // Whitelist allowed sorting fields
        String sortField = switch (sortBy) {
            case "name" -> "fileName";
            case "date" -> "uploadedAt";
            case "size" -> "fileSize";
            case "type" -> "contentType";
            default -> throw new MethodArgumentNotValidException(
                    "Invalid sort field. Use name, date, size, or type"
            );
        };

        Sort sort = order.equals("asc")
                ? Sort.by(sortField).ascending()
                : Sort.by(sortField).descending();

        return fileRepository
                .getStoredFilesByAccount(userId, status, sort)
                .stream()
                .map(FileMapper::fileToFileDto)
                .toList();
    }

    public List<StoredFileDto> getFilesByFolder(UUID userId, UUID folderId, FileStatus status) {
        if (userId == null || !userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("Invalid User ID: " + userId);
        }

        if (status == null) {
            status = FileStatus.ACTIVE;
        } else if (status == FileStatus.TRASHED) {
            throw new MethodArgumentNotValidException("Viewing trashed files by folder is Not Allowed");
        }

        Optional<StoredFolder> folder = folderRepository.getStoredFolderByFolderidAndUserId(folderId, userId);
        if (folderId == null || folder.isEmpty()) {
            throw new InvalidParentException("Invalid Folder provided !!");
        }

        return fileRepository.getUserFilesInsideFolder(userId, folderId, status)
                .stream()
                .map(FileMapper::fileToFileDto)
                .toList();
    }

    public List<StoredFileDto> getFilesByType(UUID userId, String type, FileStatus status) {
        if (userId == null || !userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("Invalid User ID: " + userId);
        }

        if (status == null) {
            status = FileStatus.ACTIVE;
        } else if (status == FileStatus.TRASHED) {
            throw new MethodArgumentNotValidException("Viewing trashed files by type is Not Allowed");
        }

        return fileRepository.getStoredFileFromStatus(userId, status, "%" + type + "%")
                .stream()
                .map(FileMapper::fileToFileDto)
                .toList();
    }
}
