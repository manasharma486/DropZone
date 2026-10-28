package com.madn.dropzone.exception.validation;

import com.madn.dropzone.exception.DropZoneException;
import org.springframework.http.HttpStatus;

public class MethodArgumentNotValidException extends DropZoneException {

    public MethodArgumentNotValidException() {
        super("Request validation failed", HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
    }

    public MethodArgumentNotValidException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
    }

    public MethodArgumentNotValidException(String message, Throwable cause) {
        super(message, cause, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
    }
}
