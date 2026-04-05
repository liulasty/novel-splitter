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

        // 如果未配置 Token，则默认放行（或者根据安全需求默认拦截，这里根据题意简单鉴权）
        if (!StringUtils.hasText(authToken)) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (authToken.equals(token)) {
                return true;
            }
        }

        log.warn("API 访问鉴权失败，来源 IP: {}, 请求路径: {}", request.getRemoteAddr(), request.getRequestURI());

        // 返回 401 统一响应结构
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");
        ApiResponse<Void> apiResponse = ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), "未授权访问：无效的 Token");
        
        try (PrintWriter writer = response.getWriter()) {
            writer.write(objectMapper.writeValueAsString(apiResponse));
            writer.flush();
        }
        
        return false;
    }
}
