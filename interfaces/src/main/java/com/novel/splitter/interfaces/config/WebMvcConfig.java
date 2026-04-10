package com.novel.splitter.interfaces.config;

import com.novel.splitter.interfaces.common.AuthInterceptor;
import com.novel.splitter.interfaces.common.RequestDebugInterceptor;
import com.novel.splitter.interfaces.common.TraceIdInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 统一配置类
 * <p>
 * 注册拦截器、跨域配置等。
 * </p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;

    @Autowired
    private TraceIdInterceptor traceIdInterceptor;

    @Autowired
    private RequestDebugInterceptor requestDebugInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(traceIdInterceptor)
                .addPathPatterns("/api/**");

        // debug 请求日志应在鉴权前执行，便于记录鉴权失败请求
        registry.addInterceptor(requestDebugInterceptor)
                .addPathPatterns("/api/**");

        // 注册鉴权拦截器，拦截所有 /api/** 请求，可以根据需要排除特定路径如登录
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/login", "/api/public/**"); // 预留排查路径
    }
}
