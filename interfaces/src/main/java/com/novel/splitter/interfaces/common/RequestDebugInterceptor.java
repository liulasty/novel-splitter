package com.novel.splitter.interfaces.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 为每个 API 请求输出 debug 级别的结构化日志，便于排查问题。
 */
@Slf4j
@Component
public class RequestDebugInterceptor implements HandlerInterceptor {

    private static final String START_MS_ATTR = RequestDebugInterceptor.class.getName() + ".startMs";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_MS_ATTR, System.currentTimeMillis());
        if (log.isDebugEnabled()) {
            log.debug("API request start, traceId={}, req={}",
                    MDC.get(TraceIdInterceptor.TRACE_ID_KEY),
                    requestSummary(request));
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        long startMs = 0L;
        Object attr = request.getAttribute(START_MS_ATTR);
        if (attr instanceof Long l) {
            startMs = l;
        }
        long costMs = startMs > 0 ? Math.max(0, System.currentTimeMillis() - startMs) : -1;

        if (log.isDebugEnabled()) {
            if (ex != null) {
                log.debug("API request end, traceId={}, status={}, costMs={}, req={}, ex={}",
                        MDC.get(TraceIdInterceptor.TRACE_ID_KEY),
                        response.getStatus(),
                        costMs,
                        requestSummary(request),
                        ex.getClass().getSimpleName());
            } else {
                log.debug("API request end, traceId={}, status={}, costMs={}, req={}",
                        MDC.get(TraceIdInterceptor.TRACE_ID_KEY),
                        response.getStatus(),
                        costMs,
                        requestSummary(request));
            }
        }
    }

    private String requestSummary(HttpServletRequest request) {
        if (request == null) {
            return "<no-request>";
        }
        String method = safe(request.getMethod());
        String uri = safe(request.getRequestURI());
        String query = safe(request.getQueryString());
        Map<String, String> params = sanitizeParams(request.getParameterMap());

        String base = method + " " + uri + (query.isBlank() ? "" : "?" + query);
        if (params.isEmpty()) {
            return base;
        }
        return base + " params=" + params;
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
}

