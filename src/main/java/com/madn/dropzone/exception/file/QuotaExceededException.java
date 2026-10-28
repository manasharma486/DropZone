package com.madn.dropzone.exception.file;

import com.madn.dropzone.exception.DropZoneException;
import org.springframework.http.HttpStatus;

public class QuotaExceededException extends DropZoneException {

    public QuotaExceededException() {
        super("Storage quota exceeded", HttpStatus.PAYLOAD_TOO_LARGE, "QUOTA_EXCEEDED");
    }

    public QuotaExceededException(String message) {
        super(message, HttpStatus.PAYLOAD_TOO_LARGE, "QUOTA_EXCEEDED");
    }

    public QuotaExceededException(long requiredBytes, long availableBytes) {
        super(String.format("Storage quota exceeded. Required: %d bytes, Available: %d bytes",
                requiredBytes, availableBytes),
                HttpStatus.PAYLOAD_TOO_LARGE, "QUOTA_EXCEEDED");
    }
}
