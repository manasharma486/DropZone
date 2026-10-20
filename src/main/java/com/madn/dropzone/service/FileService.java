package com.madn.dropzone.service;

import com.madn.dropzone.entity.StoredFile;
import com.madn.dropzone.repository.FileRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class FileService {
    private StorageService storageService;
    private FileRepository fileRepository;

    public FileService(StorageService storageService , FileRepository fileRepository) {
        this.fileRepository = fileRepository;
        this.storageService = storageService;
    }

    public String uploadFile(MultipartFile file){

        try {
            String storageKey = storageService.saveToStorage(file);
            String checkSum = storageService.calculateChecksum(file);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        StoredFile storedFile = new StoredFile(file, storageKey, c);
    }


}
