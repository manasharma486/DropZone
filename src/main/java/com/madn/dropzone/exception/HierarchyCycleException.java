package com.madn.dropzone.exception;

import org.springframework.http.HttpStatus;

public class HierarchyCycleException extends DropZoneException {

    public HierarchyCycleException() {
        super("Cannot move a folder into itself or one of its descendants", HttpStatus.BAD_REQUEST, "HIERARCHY_CYCLE_DETECTED");
    }

    public HierarchyCycleException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "HIERARCHY_CYCLE_DETECTED");
    }
}
