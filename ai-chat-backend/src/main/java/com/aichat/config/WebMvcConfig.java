package com.aichat.config;

import com.aichat.common.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebMvcConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 登录接口本身不需要token，其余/chat/**全部需要登录
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/chat/**")
                .excludePathPatterns("/chat/login");
    }
}
