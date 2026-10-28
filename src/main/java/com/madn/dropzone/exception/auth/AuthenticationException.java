package com.madn.dropzone.exception.auth;

import com.madn.dropzone.exception.DropZoneException;
import org.springframework.http.HttpStatus;

public class AuthenticationException extends DropZoneException {

    public AuthenticationException() {
        super("Full authentication is required to access this resource", HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
    }

    public AuthenticationException(String message) {
        super(message, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
    }
}
