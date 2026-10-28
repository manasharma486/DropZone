package com.madn.dropzone.exception;

import org.springframework.http.HttpStatus;

public class DataIntegrityViolationException extends DropZoneException {

    public DataIntegrityViolationException() {
        super("The operation could not be completed due to a data conflict or uniqueness constraint",
                HttpStatus.CONFLICT, "DATA_CONFLICT");
    }

    public DataIntegrityViolationException(String message) {
        super(message, HttpStatus.CONFLICT, "DATA_CONFLICT");
    }

    public DataIntegrityViolationException(String message, Throwable cause) {
        super(message, cause, HttpStatus.CONFLICT, "DATA_CONFLICT");
    }
}
