package com.madn.dropzone.exception;

import org.springframework.http.HttpStatus;

public class AccessDeniedException extends DropZoneException {

    public AccessDeniedException() {
        super("You do not have permission to access this resource", HttpStatus.FORBIDDEN, "ACCESS_DENIED");
    }

    public AccessDeniedException(String message) {
        super(message, HttpStatus.FORBIDDEN, "ACCESS_DENIED");
    }

    public AccessDeniedException(String message, HttpStatus status, String errorCode) {
        super(message, status, errorCode);
    }
}
