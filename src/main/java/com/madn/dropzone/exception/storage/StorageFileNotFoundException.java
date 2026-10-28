package com.madn.dropzone.exception.storage;

import com.madn.dropzone.exception.DropZoneException;
import org.springframework.http.HttpStatus;

public class StorageFileNotFoundException extends DropZoneException {

    public StorageFileNotFoundException() {
        super("File content unavailable in storage", HttpStatus.NOT_FOUND, "STORAGE_FILE_NOT_FOUND");
    }

    public StorageFileNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "STORAGE_FILE_NOT_FOUND");
    }

    public StorageFileNotFoundException(String message, Throwable cause) {
        super(message, cause, HttpStatus.NOT_FOUND, "STORAGE_FILE_NOT_FOUND");
    }

    public StorageFileNotFoundException(Object fileIdentifier) {
        super(String.format("Physical file content not found for identifier: %s", fileIdentifier),
                HttpStatus.NOT_FOUND, "STORAGE_FILE_NOT_FOUND");
    }
}
