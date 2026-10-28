package com.madn.dropzone.exception;

import org.springframework.http.HttpStatus;

public class HttpMessageNotReadableException extends DropZoneException {

    public HttpMessageNotReadableException() {
        super("Request payload contains invalid or unreadable JSON", HttpStatus.BAD_REQUEST, "MALFORMED_JSON");
    }

    public HttpMessageNotReadableException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "MALFORMED_JSON");
    }

    public HttpMessageNotReadableException(String message, Throwable cause) {
        super(message, cause, HttpStatus.BAD_REQUEST, "MALFORMED_JSON");
    }
}
