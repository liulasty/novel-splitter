package com.novel.splitter.interfaces.common;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.slf4j.MDC;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

/**
 * 全局异常拦截处理类
 * 使用 @RestControllerAdvice 拦截所有控制器抛出的异常
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private String traceId() {
        return MDC.get(TraceIdInterceptor.TRACE_ID_KEY);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String errorMsg = e.getBindingResult().getFieldErrors().stream()
                .map(this::buildFieldErrorMessage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("请求参数不合法");
        log.warn("请求参数校验失败, traceId={}, message={}", traceId(), errorMsg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), errorMsg));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(BindException e) {
        String errorMsg = e.getBindingResult().getFieldErrors().stream()
                .map(this::buildFieldErrorMessage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("请求参数绑定失败");
        log.warn("请求参数绑定失败, traceId={}, message={}", traceId(), errorMsg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), errorMsg));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(ConstraintViolationException e) {
        String errorMsg = e.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .filter(message -> message != null && !message.isBlank())
                .findFirst()
                .orElse("请求参数约束校验失败");
        log.warn("请求参数约束校验失败, traceId={}, message={}", traceId(), errorMsg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), errorMsg));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatusException(ResponseStatusException e) {
        String errorMsg = e.getReason() != null && !e.getReason().isBlank() ? e.getReason() : "请求处理失败";
        log.warn("请求处理异常, traceId={}, message={}", traceId(), errorMsg);
        return ResponseEntity.status(e.getStatusCode())
                .body(ApiResponse.error(e.getStatusCode().value(), errorMsg));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
        String errorMsg = e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage() : "请求参数不合法";
        log.warn("非法参数异常, traceId={}, message={}", traceId(), errorMsg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), errorMsg));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAllExceptions(Exception e) {
        log.error("系统发生未预期的异常, traceId={}", traceId(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "服务器繁忙，请稍后再试"));
    }

    private String buildFieldErrorMessage(FieldError fieldError) {
        if (fieldError == null) {
            return null;
        }
        String defaultMessage = fieldError.getDefaultMessage();
        if (defaultMessage == null || defaultMessage.isBlank()) {
            return fieldError.getField() + " 不合法";
        }
        return defaultMessage;
    }
}
