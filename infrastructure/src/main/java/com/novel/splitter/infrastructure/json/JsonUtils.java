package com.novel.splitter.infrastructure.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Path;

/**
 * JSON 工具类
 * <p>
 * 封装 Jackson ObjectMapper，提供统一的序列化配置。
 * </p>
 */
public class JsonUtils {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        // 开启格式化输出（Pretty Print），方便人工阅读
        MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
        // 反序列化时忽略未知属性，增强兼容性
        MAPPER.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        // 序列化时忽略 null 字段，减少文件体积
        MAPPER.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    /**
     * 对象转 JSON 字符串
     */
    public static String toJson(Object object) {
        try {
            return MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON serialization failed", e);
        }
    }

    /**
     * JSON 字符串转对象
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON deserialization failed", e);
        }
    }

    /**
     * 将对象写入 JSON 文件
     */
    public static void writeToFile(Path path, Object object) {
        try {
            MAPPER.writeValue(path.toFile(), object);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write JSON to file: " + path, e);
        }
    }

    /**
     * 从 JSON 文件读取对象
     */
    public static <T> T readFromFile(Path path, Class<T> clazz) {
        try {
            return MAPPER.readValue(path.toFile(), clazz);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read JSON from file: " + path, e);
        }
    }
}
