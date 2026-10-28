package com.madn.dropzone.exception;

import org.springframework.http.HttpStatus;

public class EmptyFileException extends DropZoneException {

    public EmptyFileException() {
        super("Cannot upload empty file", HttpStatus.BAD_REQUEST, "EMPTY_FILE");
    }

    public EmptyFileException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "EMPTY_FILE");
    }
}
