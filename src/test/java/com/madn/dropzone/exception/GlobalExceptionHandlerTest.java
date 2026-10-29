package com.madn.dropzone.exception;

import com.madn.dropzone.dto.response.ApiErrorResponse;
import com.madn.dropzone.exception.common.DuplicateResourceException;
import com.madn.dropzone.exception.common.ResourceNotFoundException;
import com.madn.dropzone.exception.file.QuotaExceededException;
import com.madn.dropzone.exception.folder.HierarchyCycleException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler exceptionHandler;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        when(request.getRequestURI()).thenReturn("/api/test-endpoint");
        MDC.clear();
    }

    @Test
    @DisplayName("Handle ResourceNotFoundException returning 404")
    void handleResourceNotFoundException() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Resource not found");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleDropZoneException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getCode()).isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(response.getBody().getMessage()).isEqualTo("Resource not found");
        assertThat(response.getBody().getPath()).isEqualTo("/api/test-endpoint");
        assertThat(response.getBody().getTraceId()).isNotBlank();
    }

    @Test
    @DisplayName("Handle DuplicateResourceException returning 409")
    void handleDuplicateResourceException() {
        DuplicateResourceException ex = new DuplicateResourceException("File already exists");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleDropZoneException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(409);
        assertThat(response.getBody().getCode()).isEqualTo("DUPLICATE_RESOURCE");
        assertThat(response.getBody().getMessage()).isEqualTo("File already exists");
    }

    @Test
    @DisplayName("Handle QuotaExceededException returning 413")
    void handleQuotaExceededException() {
        QuotaExceededException ex = new QuotaExceededException("Quota exceeded");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleDropZoneException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(413);
        assertThat(response.getBody().getCode()).isEqualTo("QUOTA_EXCEEDED");
    }

    @Test
    @DisplayName("Handle HierarchyCycleException returning 400")
    void handleHierarchyCycleException() {
        HierarchyCycleException ex = new HierarchyCycleException("Cycle detected");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleDropZoneException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getCode()).isEqualTo("HIERARCHY_CYCLE_DETECTED");
    }

    @Test
    @DisplayName("Handle MethodArgumentNotValidException returning 400 with field errors")
    void handleMethodArgumentNotValidException() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError1 = new FieldError("user", "email", "invalid-email", false, null, null, "Must be valid email");
        FieldError fieldError2 = new FieldError("user", "name", "", false, null, null, "Name cannot be blank");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError1, fieldError2));

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleValidationException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.getBody().getFieldErrors()).hasSize(2);
        assertThat(response.getBody().getFieldErrors().get(0).getField()).isEqualTo("email");
        assertThat(response.getBody().getFieldErrors().get(0).getMessage()).isEqualTo("Must be valid email");
    }

    @Test
    @DisplayName("Handle AccessDeniedException returning 403 Forbidden")
    void handleAccessDeniedException() {
        AccessDeniedException ex = new AccessDeniedException("Access denied");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleAccessDenied(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(403);
        assertThat(response.getBody().getCode()).isEqualTo("ACCESS_DENIED");
    }

    @Test
    @DisplayName("Handle AuthenticationException returning 401 Unauthorized")
    void handleAuthenticationException() {
        AuthenticationException ex = mock(AuthenticationException.class);

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleAuthenticationException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(401);
        assertThat(response.getBody().getCode()).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    @DisplayName("Handle uncaught Exception masking details with 500 status")
    void handleUncaughtException() {
        Exception ex = new RuntimeException("Sensitive database stacktrace or secret");

        ResponseEntity<ApiErrorResponse> response = exceptionHandler.handleUncaughtException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(500);
        assertThat(response.getBody().getCode()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(response.getBody().getMessage()).doesNotContain("Sensitive database");
        assertThat(response.getBody().getMessage()).contains("An unexpected internal error occurred");
        assertThat(response.getBody().getTraceId()).isNotBlank();
    }
}
