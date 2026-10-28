package com.madn.dropzone.exception.file;

import com.madn.dropzone.exception.DropZoneException;
import org.springframework.http.HttpStatus;

public class MaxUploadSizeExceededException extends DropZoneException {

    public MaxUploadSizeExceededException() {
        super("Upload exceeds maximum allowable request size", HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE");
    }

    public MaxUploadSizeExceededException(String message) {
        super(message, HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE");
    }

    public MaxUploadSizeExceededException(String message, Throwable cause) {
        super(message, cause, HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE");
    }

    public MaxUploadSizeExceededException(long maxBytes) {
        super(String.format("Upload exceeds maximum permitted size of %d bytes", maxBytes),
                HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE");
    }
}
