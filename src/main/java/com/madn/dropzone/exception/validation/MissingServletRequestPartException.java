package com.madn.dropzone.exception.validation;

import com.madn.dropzone.exception.DropZoneException;
import org.springframework.http.HttpStatus;

public class MissingServletRequestPartException extends DropZoneException {

    public MissingServletRequestPartException() {
        super("Required request part is missing", HttpStatus.BAD_REQUEST, "MISSING_REQUEST_PART");
    }

    public MissingServletRequestPartException(String partName) {
        super(String.format("Required request part '%s' is missing", partName),
                HttpStatus.BAD_REQUEST, "MISSING_REQUEST_PART");
    }
}
