package com.madn.dropzone.dto;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import java.time.Instant;
import java.util.List;
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {
    private final Instant timestamp;
    private final int status;
    private final String error;
    private final String code;
    private final String message;
    private final String path;
    private final String traceId;
    private final List<FieldErrorItem> fieldErrors;

    @Getter
    @Builder
    public static class FieldErrorItem {
        private final String field;
        private final Object rejectedValue;
        private final String message;
    }
}