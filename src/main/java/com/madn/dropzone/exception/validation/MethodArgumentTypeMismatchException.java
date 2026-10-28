package com.madn.dropzone.exception.validation;

import com.madn.dropzone.exception.DropZoneException;
import org.springframework.http.HttpStatus;

public class MethodArgumentTypeMismatchException extends DropZoneException {

    public MethodArgumentTypeMismatchException() {
        super("Parameter has an invalid value format", HttpStatus.BAD_REQUEST, "INVALID_PARAMETER_FORMAT");
    }

    public MethodArgumentTypeMismatchException(String message) {
        super(message, HttpStatus.BAD_REQUEST, "INVALID_PARAMETER_FORMAT");
    }

    public MethodArgumentTypeMismatchException(String parameterName, Object value) {
        super(String.format("Parameter '%s' has an invalid value format: %s", parameterName, value),
                HttpStatus.BAD_REQUEST, "INVALID_PARAMETER_FORMAT");
    }
}
