package com.madn.dropzone.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AspectLoggingTest {

    private PerformanceLoggingAspect performanceAspect;
    private AuditLoggingAspect auditAspect;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private Signature signature;

    @BeforeEach
    void setUp() {
        performanceAspect = new PerformanceLoggingAspect();
        auditAspect = new AuditLoggingAspect();
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("PerformanceLoggingAspect intercepts execution and returns result")
    void performanceAspect_NormalExecution_ReturnsResult() throws Throwable {
        TrackExecutionTime annotation = mock(TrackExecutionTime.class);
        when(annotation.thresholdMs()).thenReturn(500L);

        when(joinPoint.getTarget()).thenReturn(new Object());
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("testMethod");
        when(joinPoint.proceed()).thenReturn("successResult");

        Object result = performanceAspect.measureExecutionTime(joinPoint, annotation);

        assertThat(result).isEqualTo("successResult");
    }

    @Test
    @DisplayName("PerformanceLoggingAspect propagates exception thrown by joinPoint")
    void performanceAspect_ExceptionPropagated() throws Throwable {
        TrackExecutionTime annotation = mock(TrackExecutionTime.class);
        when(annotation.thresholdMs()).thenReturn(500L);

        when(joinPoint.getTarget()).thenReturn(new Object());
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("failingMethod");
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("Operation failed"));

        assertThatThrownBy(() -> performanceAspect.measureExecutionTime(joinPoint, annotation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Operation failed");
    }

    @Test
    @DisplayName("AuditLoggingAspect logs successful execution with authenticated actor")
    void auditAspect_SuccessfulAction_WithAuthenticatedUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice@example.com", "N/A")
        );
        MDC.put("traceId", "trace-12345");

        AuditAction auditAction = mock(AuditAction.class);
        when(auditAction.action()).thenReturn("UPLOAD_FILE");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("uploadFiles");

        auditAspect.logSuccessfulAction(joinPoint, auditAction, "result");
    }

    @Test
    @DisplayName("AuditLoggingAspect logs failure with anonymous actor when unauthenticated")
    void auditAspect_FailedAction_WithAnonymousActor() {
        MDC.put("traceId", "trace-99999");

        AuditAction auditAction = mock(AuditAction.class);
        when(auditAction.action()).thenReturn("DELETE_FILE");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("deleteFile");

        auditAspect.logFailedAction(joinPoint, auditAction, new RuntimeException("File not found"));
    }
}
