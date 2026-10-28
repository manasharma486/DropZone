package com.madn.dropzone.exception;

import org.springframework.http.HttpStatus;

public class StorageException extends DropZoneException {

    public StorageException() {
        super("Storage subsystem failure", HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_IO_ERROR");
    }

    public StorageException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_IO_ERROR");
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause, HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_IO_ERROR");
    }

    public StorageException(Throwable cause) {
        super("Storage subsystem failure", cause, HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_IO_ERROR");
    }
}
