package com.madn.dropzone.exception.common;

import com.madn.dropzone.exception.DropZoneException;
import org.springframework.http.HttpStatus;

public class DuplicateResourceException extends DropZoneException {

    public DuplicateResourceException() {
        super("Resource already exists in this destination", HttpStatus.CONFLICT, "DUPLICATE_RESOURCE");
    }

    public DuplicateResourceException(String message) {
        super(message, HttpStatus.CONFLICT, "DUPLICATE_RESOURCE");
    }

    public DuplicateResourceException(String resourceType, String name) {
        super(String.format("%s with name '%s' already exists in this location", resourceType, name),
                HttpStatus.CONFLICT, "DUPLICATE_RESOURCE");
    }
}
