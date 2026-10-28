package com.madn.dropzone.exception.common;

import com.madn.dropzone.exception.DropZoneException;
import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends DropZoneException {

    public ResourceNotFoundException() {
        super("Requested resource not found", HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
    }

    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
    }

    public ResourceNotFoundException(String resourceName, Object identifier) {
        super(String.format("%s not found with identifier: %s", resourceName, identifier),
                HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
    }
}
