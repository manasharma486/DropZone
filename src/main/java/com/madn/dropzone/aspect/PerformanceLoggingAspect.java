package com.madn.dropzone.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PerformanceLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(PerformanceLoggingAspect.class);

    @Around("@annotation(trackTime)")
    public Object measureExecutionTime(ProceedingJoinPoint joinPoint, TrackExecutionTime trackTime) throws Throwable {
        long start = System.currentTimeMillis();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();

        try {
            return joinPoint.proceed();
        } finally {
            long duration = System.currentTimeMillis() - start;

            if (duration >= trackTime.thresholdMs()) {
                log.warn("PERF SLOW OPERATION: {}.{}() executed in {} ms (Threshold: {} ms)",
                        className, methodName, duration, trackTime.thresholdMs());
            } else {
                log.debug("PERF: {}.{}() executed in {} ms", className, methodName, duration);
            }
        }
    }
}
