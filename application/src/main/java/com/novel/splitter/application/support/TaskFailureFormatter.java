package com.novel.splitter.application.support;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 统一失败信息：阶段名 + 关键参数 + 根因摘要（可观测、可定位）。
 */
public final class TaskFailureFormatter {

    private TaskFailureFormatter() {
    }

    public static String format(String stage, Map<String, String> keyParams, Throwable cause) {
        String stageName = stage != null ? stage : "UNKNOWN";
        Map<String, String> params = keyParams != null ? keyParams : Map.of();
        String root = rootCauseSummary(cause);
        if (params.isEmpty()) {
            return String.format("[%s] %s", stageName, root);
        }
        return String.format("[%s] params=%s %s", stageName, params, root);
    }

    public static String format(String stage, String key, String value, Throwable cause) {
        Map<String, String> m = new LinkedHashMap<>();
        if (key != null && value != null) {
            m.put(key, value);
        }
        return format(stage, m, cause);
    }

    public static String rootCauseSummary(Throwable cause) {
        if (cause == null) {
            return "(no exception)";
        }
        Throwable t = cause;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String type = t.getClass().getSimpleName();
        String msg = t.getMessage();
        if (msg == null || msg.isBlank()) {
            return type;
        }
        String trimmed = msg.length() > 400 ? msg.substring(0, 400) + "..." : msg;
        return type + ": " + trimmed;
    }

    public static Map<String, String> params(String... kv) {
        Objects.requireNonNull(kv, "kv");
        if (kv.length % 2 != 0) {
            throw new IllegalArgumentException("params must be key-value pairs");
        }
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            if (kv[i] != null && kv[i + 1] != null) {
                m.put(kv[i], kv[i + 1]);
            }
        }
        return m;
    }
}
