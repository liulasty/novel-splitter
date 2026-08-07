package com.novel.splitter.interfaces.common;

import com.novel.splitter.domain.exception.BusinessErrorCode;
import com.novel.splitter.domain.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.Objects;
import java.util.stream.Collectors;

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
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e, HttpServletRequest request) {
        String errorMsg = e.getBindingResult().getFieldErrors().stream()
                .map(this::buildFieldErrorMessage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("请求参数不合法");
        log.warn("请求参数校验失败, traceId={}, req={}, message={}", traceId(), requestSummary(request), errorMsg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), errorMsg));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(BindException e, HttpServletRequest request) {
        String errorMsg = e.getBindingResult().getFieldErrors().stream()
                .map(this::buildFieldErrorMessage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("请求参数绑定失败");
        log.warn("请求参数绑定失败, traceId={}, req={}, message={}", traceId(), requestSummary(request), errorMsg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), errorMsg));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(ConstraintViolationException e, HttpServletRequest request) {
        String errorMsg = e.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .filter(message -> message != null && !message.isBlank())
                .findFirst()
                .orElse("请求参数约束校验失败");
        log.warn("请求参数约束校验失败, traceId={}, req={}, message={}", traceId(), requestSummary(request), errorMsg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), errorMsg));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatusException(ResponseStatusException e, HttpServletRequest request) {
        String errorMsg = e.getReason() != null && !e.getReason().isBlank() ? e.getReason() : "请求处理失败";
        log.warn("请求处理异常, traceId={}, req={}, message={}", traceId(), requestSummary(request), errorMsg);
        return ResponseEntity.status(e.getStatusCode())
                .body(ApiResponse.error(e.getStatusCode().value(), errorMsg));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException e, HttpServletRequest request) {
        String errorMsg = e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage() : "请求参数不合法";
        log.warn("非法参数异常, traceId={}, req={}, message={}", traceId(), requestSummary(request), errorMsg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), errorMsg));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException e, HttpServletRequest request) {
        log.warn("请求的资源不存在, traceId={}, req={}, resource={}", traceId(), requestSummary(request), e.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(HttpStatus.NOT_FOUND.value(), "请求的资源不存在"));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e, HttpServletRequest request) {
        BusinessErrorCode ec = e.getErrorCode();
        log.warn("业务异常, traceId={}, req={}, code={}, message={}", traceId(), requestSummary(request), ec.getCode(), e.getMessage());
        return ResponseEntity.status(e.getHttpStatus())
                .body(ApiResponse.error(ec.getCode(), e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAllExceptions(Exception e, HttpServletRequest request) {
        log.error("系统发生未预期的异常, traceId={}, req={}", traceId(), requestSummary(request), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "服务器繁忙，请稍后再试"));
    }

    private String requestSummary(HttpServletRequest request) {
        if (request == null) {
            return "<no-request>";
        }
        String method = safe(request.getMethod());
        String uri = safe(request.getRequestURI());
        String query = safe(request.getQueryString());

        Map<String, String> params = sanitizeParams(request.getParameterMap());
        if (query.isBlank() && params.isEmpty()) {
            return method + " " + uri;
        }
        if (!params.isEmpty()) {
            return method + " " + uri + (query.isBlank() ? "" : "?" + query) + " params=" + params;
        }
        return method + " " + uri + "?" + query;
    }

    private Map<String, String> sanitizeParams(Map<String, String[]> parameterMap) {
        if (parameterMap == null || parameterMap.isEmpty()) {
            return Map.of();
        }
        Map<String, String> sanitized = new TreeMap<>();
        for (var entry : parameterMap.entrySet()) {
            String key = entry.getKey();
            String[] values = entry.getValue();
            if (key == null) {
                continue;
            }
            if (isSensitiveKey(key)) {
                sanitized.put(key, "***");
                continue;
            }
            String joined = values == null ? "" : Arrays.stream(values).map(this::safe).collect(Collectors.joining(","));
            sanitized.put(key, joined);
        }
        return sanitized;
    }

    private boolean isSensitiveKey(String key) {
        String k = key.toLowerCase();
        return k.contains("password")
                || k.contains("passwd")
                || k.contains("pwd")
                || k.contains("token")
                || k.contains("authorization")
                || k.contains("secret")
                || k.contains("apikey")
                || k.contains("api_key")
                || k.contains("accesskey")
                || k.contains("access_key");
    }

    private String safe(String s) {
        return s == null ? "" : s;
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
