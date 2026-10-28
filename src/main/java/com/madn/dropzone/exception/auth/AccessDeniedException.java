package com.madn.dropzone.exception.auth;

import com.madn.dropzone.exception.DropZoneException;
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
