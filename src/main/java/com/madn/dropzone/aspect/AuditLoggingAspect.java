package com.madn.dropzone.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger("AUDIT_LOGGER");

    @AfterReturning(pointcut = "@annotation(auditAction)", returning = "result")
    public void logSuccessfulAction(JoinPoint joinPoint, AuditAction auditAction, Object result) {
        String actor = resolveCurrentPrincipal();
        String traceId = MDC.get("traceId");
        String methodName = joinPoint.getSignature().getName();

        log.info("AUDIT SUCCESS [action={}, actor={}, method={}, traceId={}]",
                auditAction.action(), actor, methodName, traceId);
    }

    @AfterThrowing(pointcut = "@annotation(auditAction)", throwing = "exception")
    public void logFailedAction(JoinPoint joinPoint, AuditAction auditAction, Throwable exception) {
        String actor = resolveCurrentPrincipal();
        String traceId = MDC.get("traceId");
        String methodName = joinPoint.getSignature().getName();

        log.warn("AUDIT FAILURE [action={}, actor={}, method={}, error={}, traceId={}]",
                auditAction.action(), actor, methodName, exception.getMessage(), traceId);
    }

    private String resolveCurrentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth.getName();
        }
        return "ANONYMOUS";
    }
}
