package com.madn.dropzone.exception;

import org.springframework.http.HttpStatus;

public class DropZoneException extends RuntimeException{
    private final HttpStatus status;
    private final String errorCode;

    public DropZoneException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public DropZoneException(String message, Throwable cause, HttpStatus status, String errorCode) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
