package com.enterprise.quota.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Autowired
    private AuthInterceptor authInterceptor;
    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // CORS配置：由于前后端同源，不需要credentials
        // 如果需要跨域支持，可以明确指定允许的域名
        registry.addMapping("/**")
                .allowedOriginPatterns("*")  // 使用allowedOriginPatterns支持模式匹配
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)  // 前后端同源时不需要credentials，避免与allowedOriginPatterns冲突
                .maxAge(3600);
    }
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
    }
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                    "/api/auth/**",
                    "/login.html",
                    "/static/**",
                    "/css/**",
                    "/js/**",
                    "/images/**"
                );
    }
}

