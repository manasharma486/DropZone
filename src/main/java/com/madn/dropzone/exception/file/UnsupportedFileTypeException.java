package com.madn.dropzone.exception.file;

import com.madn.dropzone.exception.DropZoneException;
import org.springframework.http.HttpStatus;

public class UnsupportedFileTypeException extends DropZoneException {

    public UnsupportedFileTypeException() {
        super("File type not permitted", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE");
    }

    public UnsupportedFileTypeException(String message) {
        super(message, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE");
    }
}
