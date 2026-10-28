package com.madn.dropzone.exception;

import org.springframework.http.HttpStatus;

public class InvalidParentException extends DropZoneException {

    public InvalidParentException() {
        super("Destination folder does not exist or access is denied", HttpStatus.BAD_REQUEST, "INVALID_PARENT_FOLDER");
    }

    public InvalidParentException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "INVALID_PARENT_FOLDER");
    }

    public InvalidParentException(Object parentId) {
        super(String.format("Destination folder '%s' does not exist or access is denied", parentId),
                HttpStatus.BAD_REQUEST, "INVALID_PARENT_FOLDER");
    }
}
