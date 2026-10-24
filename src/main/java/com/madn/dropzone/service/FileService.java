package com.madn.dropzone.service;
import com.madn.dropzone.dto.StoredFileDto;
import com.madn.dropzone.entity.StorageAccount;
import com.madn.dropzone.entity.StoredFile;
import com.madn.dropzone.entity.StoredFolder;
import com.madn.dropzone.enums.FileStatus;
import com.madn.dropzone.mapper.FileMapper;
import com.madn.dropzone.repository.FileRepository;
import com.madn.dropzone.repository.FolderRepository;
import com.madn.dropzone.repository.StorageAccountRepository;
import com.madn.dropzone.repository.UserRepository;
import org.springframework.boot.data.autoconfigure.web.DataWebProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.madn.dropzone.mapper.FileMapper.fileToFileDto;

@Service
public class FileService {
    private final UserRepository userRepository;
    private StorageService storageService;
    private FileRepository fileRepository;
    private StorageAccountRepository storageAccountRepository;
    private FolderRepository folderRepository;


    public FileService(StorageService storageService ,
                       FileRepository fileRepository ,
                       StorageAccountRepository storageAccountRepository,
                       FolderRepository folderRepository, UserRepository userRepository) {
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
                        new RuntimeException("Storage account not found"));

        long additionalBytes = 0;

        for (MultipartFile file : files) {

            if (file.isEmpty()) {
                throw new RuntimeException("File cannot be empty");
            }

            additionalBytes += file.getSize();

            if (additionalBytes > account.getQuotaBytes() - account.getUsedBytes()) {
                throw new RuntimeException("Storage quota exceeded");
            }

            String checksum = storageService.calculateChecksum(file);

            Optional<StoredFile> fileOptional =
                    fileRepository.existFileInAccount(checksum, userID);

            if (fileOptional.isPresent()) {

                StoredFile existingFile = fileOptional.get();

                if (existingFile.getStatus() == FileStatus.DELETED) {
                    throw new RuntimeException(
                            "File cannot be added; it is already present in the bin!"
                    );
                }

                throw new RuntimeException(
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
        StoredFile file = fileRepository.getStoredFileByIdAndUserId(userid,fileid).orElseThrow(() -> new RuntimeException("File Does not exist"));
        file.setStatus(FileStatus.DELETED);
        fileRepository.save(file);
        return "File Deleated SuccessFully !!";
    }

    public List<StoredFileDto> getDriveFiles(UUID userid) {
        boolean check  = storageAccountRepository.existsById(userid);
        if(check) {
            List<StoredFileDto> storedFileDtoList = fileRepository.getStoredFileFromStatus(userid , FileStatus.ACTIVE , "").
                    stream().map((file)-> fileToFileDto(file)).toList();

            return storedFileDtoList;
        }

        else {
            throw new RuntimeException("User Does not Exists !!");
        }
    }

    public StoredFileDto getDriveFile(UUID userid, UUID fileid) {
        StoredFile storedFile = fileRepository.getStoredFileByIdAndUserId(userid , fileid).orElseThrow(()->
                    new IllegalArgumentException("File does not exists"));


        return FileMapper.fileToFileDto(storedFile);
    }


    public String updateFileName(UUID userid, UUID fileid, String name) {

        // Check for null or empty filename
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Filename cannot be empty");
        }

        // Remove leading and trailing whitespace
        name = name.trim();

        // Check filename length
        if (name.length() > 255) {
            throw new IllegalArgumentException("Filename cannot exceed 255 characters");
        }

        // Prevent special filenames
        if (name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("Invalid filename");
        }

        // Reject characters that are invalid or problematic in filenames
        if (name.matches(".*[\\\\/:*?\"<>|].*")) {
            throw new IllegalArgumentException(
                    "Filename cannot contain these characters: \\ / : * ? \" < > |"
            );
        }

        // Reject filenames ending with a dot or space
        if (name.endsWith(".") || name.endsWith(" ")) {
            throw new IllegalArgumentException(
                    "Filename cannot end with a dot or space"
            );
        }

        // Your file-renaming logic goes here

        StoredFile storedFile = fileRepository.getStoredFileByIdAndUserId(userid , fileid).orElseThrow(() ->
                new IllegalArgumentException("No File Exist"));

        storedFile.setFileName(name);
        fileRepository.save(storedFile);

        return "File Name changed to: " + name;
    }

    @Transactional
    public String moveFile(UUID userId, UUID fileId, UUID folderId) {

       
        StoredFile storedFile = fileRepository
                .getStoredFileByIdAndUserId(userId, fileId)
                .orElseThrow(() ->
                        new IllegalArgumentException("File does not exist or access is denied"));

    
        if (folderId == null) {
            storedFile.setFolder(null);
        }

        else {
            StoredFolder folder = folderRepository
                    .getStoredFolderByFolderidAndUserId(folderId, userId)
                    .orElseThrow(() ->
                            new IllegalArgumentException("Destination folder does not exist"));

            storedFile.setFolder(folder);
        }

      
        fileRepository.save(storedFile);

        return "File moved successfully!";
    }


    public String copyFile(UUID userid, UUID fileid, UUID folderid) {

        StorageAccount account = storageAccountRepository.findByUserId(userid).orElseThrow(
                ()-> new IllegalArgumentException("User Does not exist")
        );

        StoredFile storedFile = fileRepository
                .getStoredFileByIdAndUserId(userid, fileid)
                .orElseThrow(() ->
                        new IllegalArgumentException("File does not exist or access is denied"));
        
        
        StoredFile copyFile = FileMapper.StoredFileCopy(storedFile , null);
        account.storeFileInAccount(copyFile);



        if (folderid != null) {
            StoredFolder folder = folderRepository
                    .getStoredFolderByFolderidAndUserId(folderid, userid)
                    .orElseThrow(() ->
                            new IllegalArgumentException("Destination folder does not exist"));
            
            
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
                        new IllegalArgumentException("File does not exist or access is denied"));

        // download logic

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

        }
        catch (IOException e) {
            throw new UncheckedIOException( "Unable to download the file !! " , e);
        }

    }


    public List<StoredFileDto> searchFiles(UUID userId, String query , FileStatus status) {

        if (status == null) {
            status = FileStatus.ACTIVE;
        } else if (status == FileStatus.TRASHED) {
            throw new IllegalArgumentException("Not Allowed");
        }

        if (userId == null || !userRepository.existsById(userId)) {
            throw new IllegalArgumentException("Invalid User ID");
        }

        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Search query cannot be empty");
        }

        return fileRepository
                .getStoredFilesByFileNameAndAccount(
                        query.trim(), userId , status
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
            throw new IllegalArgumentException("Invalid User ID");
        }

        if (sortBy == null || order == null) {
            throw new IllegalArgumentException(
                    "Sort field and order are required"
            );
        }

        if (status == null) {
            status = FileStatus.ACTIVE;
        }else if (status == FileStatus.TRASHED) {
            throw new IllegalArgumentException("Not Allowed");
        }

        sortBy = sortBy.trim().toLowerCase();
        order = order.trim().toLowerCase();

        if (!order.equals("asc") && !order.equals("desc")) {
            throw new IllegalArgumentException("Invalid order type");
        }

        // Whitelist allowed sorting fields
        String sortField = switch (sortBy) {
            case "name" -> "fileName";
            case "date" -> "uploadedAt";
            case "size" -> "fileSize";
            case "type" -> "contentType";
            default -> throw new IllegalArgumentException(
                    "Invalid sort field. Use name, date, size, or type"
            );
        };

        Sort sort = order.equals("asc")
                ? Sort.by(sortField).ascending()
                : Sort.by(sortField).descending();

        return fileRepository
                .getStoredFilesByAccount(userId, status , sort)
                .stream()
                .map(FileMapper::fileToFileDto)
                .toList();
    }

    public List<StoredFileDto> getFilesByFolder(UUID userId, UUID folderId , FileStatus status) {
        if (userId == null || !userRepository.existsById(userId)) {
            throw new IllegalArgumentException("Invalid User ID");
        }

        if (status == null) {
            status = FileStatus.ACTIVE;
        } else if (status == FileStatus.TRASHED) {
            throw new IllegalArgumentException("Not Allowed");
        }

        Optional<StoredFolder> folder = folderRepository.getStoredFolderByFolderidAndUserId(folderId,userId);
        if (folderId ==null || folder.isEmpty()) {
            throw new IllegalArgumentException("Invalid Folder provided !!");
        }



    return fileRepository.getUserFilesInsideFolder(userId , folderId ,status).
            stream().
            map(FileMapper::fileToFileDto)
            .toList();
    }

    public List<StoredFileDto> getFilesByType(UUID userId, String type , FileStatus status) {
        if (userId == null || !userRepository.existsById(userId)) {
            throw new IllegalArgumentException("Invalid User ID");
        }

        if (status == null) {
            status = FileStatus.ACTIVE;
        } else if (status == FileStatus.TRASHED) {
            throw new IllegalArgumentException("Not Allowed");
        }

        List<StoredFileDto> storedFileDtoList;
        storedFileDtoList = fileRepository.getStoredFileFromStatus(userId , status , "%" + type + "%").
                stream().map((file)-> fileToFileDto(file)).toList();
        return storedFileDtoList;

    }
}
