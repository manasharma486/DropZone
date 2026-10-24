package com.madn.dropzone.controller;

import com.madn.dropzone.dto.StoredFileDto;
import com.madn.dropzone.service.FileService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    //upload
    @PostMapping(value = "/upload",
    consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadFile(@RequestParam("file") List<MultipartFile> file,
    @RequestParam("userid")UUID userid) {
        String s = fileService.uploadFile(file , userid);

        return ResponseEntity.ok(s);
    }
    //get drive files all
    @GetMapping
    public ResponseEntity<List<StoredFileDto>> getFilesInDrive(@RequestParam("userid") UUID userid) {
        List<StoredFileDto> storedFileDtoList = fileService.getDriveFiles(userid);

        return ResponseEntity.ok(storedFileDtoList);
    }

    //get single drive file

    @GetMapping("/{fileid}")
    public ResponseEntity<StoredFileDto> getFilesInDrive(@RequestParam("userid") UUID userid ,
                                                               @PathVariable("fileid") UUID fileid) {
        StoredFileDto storedFileDtoList = fileService.getDriveFile(userid , fileid);

        return ResponseEntity.ok(storedFileDtoList);
    }

    //rename file

    @PatchMapping("/{fileid}")
    public ResponseEntity<String> updateFileName(@RequestParam("userid") UUID userid ,
                                                        @RequestParam(value = "name") String name,
                                                         @PathVariable("fileid") UUID fileid) {
        String s = fileService.updateFileName(userid , fileid , name);

        return ResponseEntity.ok(s);
    }

    // move file from 1 folder to another

    @PatchMapping("/{fileid}/move")
    public ResponseEntity<String> moveFile(@RequestParam("userid") UUID userid,
                                           @PathVariable("fileid") UUID fileid,
                                           @RequestParam(value = "folderid" , required = false , defaultValue = "") UUID folderid) {
        String s  = fileService.moveFile(userid , fileid , folderid);

        return ResponseEntity.ok(s);
    }

    // copy file

    @PostMapping("/{fileid}/copy")
    public ResponseEntity<String> copyFile(@RequestParam("userid") UUID userid,
                                           @PathVariable("fileid") UUID fileid,
                                           @RequestParam(value = "folderid" , required = false , defaultValue = "") UUID folderid) {
        String s  = fileService.copyFile(userid , fileid , folderid);

        return ResponseEntity.ok(s);
    }

    // download file

    @GetMapping("/{fileid}/download")
    public ResponseEntity<String> downloadFile(@RequestParam("userid") UUID userid,
                                               @PathVariable("fileid") UUID fileid) {
        String s = fileService.downloadFile(userid , fileid);

        return  ResponseEntity.ok(s);
    }

    // Search files by filename
    @GetMapping("/search")
    public ResponseEntity<List<StoredFileDto>> searchFiles(
            @RequestParam("userid") UUID userId,
            @RequestParam("q") String query) {

        return ResponseEntity.ok(
                fileService.searchFiles(userId, query)
        );
    }

    // Sort drive files by name
    @GetMapping(params = "sortBy")
    public ResponseEntity<List<StoredFileDto>> sortFiles(
            @RequestParam("userid") UUID userId,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String order) {

        return ResponseEntity.ok(
                fileService.sortDriveFiles(userId, sortBy, order)
        );
    }

    // Get files inside a specific folder
    @GetMapping(params = "folderId")
    public ResponseEntity<List<StoredFileDto>> getFilesByFolder(
            @RequestParam("userid") UUID userId,
            @RequestParam("folderId") UUID folderId) {

        return ResponseEntity.ok(
                fileService.getFilesByFolder(userId, folderId)
        );
    }

    // Filter files by type
    @GetMapping(params = "type")
    public ResponseEntity<List<StoredFileDto>> getFilesByType(
            @RequestParam("userid") UUID userId,
            @RequestParam("type") String type) {

        return ResponseEntity.ok(
                fileService.getFilesByType(userId, type)
        );
    }


    //delete the file

    @DeleteMapping()
    public ResponseEntity<String> deleteFile(@RequestParam("fileid") UUID fileid , @RequestParam("userid") UUID userid) {
        String s = fileService.deleteFile(fileid , userid);

        return ResponseEntity.ok(s);
    }

}
