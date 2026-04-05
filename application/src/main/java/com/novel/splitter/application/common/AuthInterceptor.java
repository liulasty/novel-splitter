package com.novel.splitter.application.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.http.HttpMethod;

import java.io.PrintWriter;

/**
 * 轻量级 API 访问鉴权拦截器
 * <p>
 * 验证请求 Header 中的 Authorization: Bearer <Token>
 * </p>
 */
@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Value("${api.auth.token:}")
    private String authToken;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行 OPTIONS 预检请求
        if (HttpMethod.OPTIONS.name().equals(request.getMethod())) {
            return true;
        }

        // 如果未配置 Token，拒绝访问，防止未配置导致的鉴权绕过（Fail-Closed）
        if (!StringUtils.hasText(authToken)) {
            log.error("系统未配置鉴权 Token (api.auth.token)，拒绝所有 API 访问。请检查 application.yml 或环境变量配置。");
            return rejectRequest(request, response, "系统配置错误：未配置鉴权 Token");
        }

        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (authToken.equals(token)) {
                return true;
            }
        }

        return rejectRequest(request, response, "未授权访问：无效的 Token");
    }

    private boolean rejectRequest(HttpServletRequest request, HttpServletResponse response, String message) throws Exception {
        log.warn("API 访问鉴权失败，来源 IP: {}, 请求路径: {}", request.getRemoteAddr(), request.getRequestURI());

        // 返回 401 统一响应结构
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");
        ApiResponse<Void> apiResponse = ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), message);

        try (PrintWriter writer = response.getWriter()) {
            writer.write(objectMapper.writeValueAsString(apiResponse));
            writer.flush();
        }

        return false;
    }
}
