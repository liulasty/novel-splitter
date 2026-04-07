package com.novel.splitter.interfaces.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 全局统一响应拦截器
 * <p>
 * 将所有 Controller 层的裸数据返回值自动包装为 ApiResponse 结构。
 * 针对 String 类型做特殊处理以避免 ClassCastException。
 * </p>
 */
@RestControllerAdvice(basePackages = "com.novel.splitter.application.controller")
public class GlobalResponseAdvice implements ResponseBodyAdvice<Object> {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // 如果返回值已经是 ApiResponse、ResponseEntity 或 SseEmitter 类型，则不进行拦截和二次包装
        Class<?> parameterType = returnType.getParameterType();
        return !parameterType.equals(ApiResponse.class)
                && !parameterType.equals(ResponseEntity.class)
                && !parameterType.equals(SseEmitter.class);
    }

    @SneakyThrows
    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        
        // 针对 Spring 的 StringHttpMessageConverter 的特殊处理
        // 如果 Controller 原本返回 String，Spring 会使用 StringHttpMessageConverter
        // 此时如果强行返回 ApiResponse 对象会报 ClassCastException，所以需要手动序列化为 JSON 字符串
        if (returnType.getParameterType().equals(String.class)) {
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return objectMapper.writeValueAsString(ApiResponse.success("操作成功", body));
        }

        // 其他类型直接包装并返回
        return ApiResponse.success("操作成功", body);
    }
}
