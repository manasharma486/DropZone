package com.madn.dropzone.controller;

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

    @PostMapping(value = "/upload",
    consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadFile(@RequestParam("file") List<MultipartFile> file,
    @RequestParam("userid")UUID userid) {
        String s = fileService.uploadFile(file , userid);

        return ResponseEntity.ok(s);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteFile(@RequestParam("fileid") UUID fileid , @RequestParam("userid") UUID userid) {
        String s = fileService.deleteFile(fileid , userid);

        return ResponseEntity.ok(s);
    }

}
