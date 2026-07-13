package com.devmatch.config;

import com.devmatch.service.ai.AiReviewRateLimitInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AiReviewRateLimitInterceptor aiReviewRateLimitInterceptor;

    public WebConfig(AiReviewRateLimitInterceptor aiReviewRateLimitInterceptor) {
        this.aiReviewRateLimitInterceptor = aiReviewRateLimitInterceptor;
    }

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadPath.toUri().toString());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(aiReviewRateLimitInterceptor)
                .addPathPatterns("/api/ai-review/**");
    }
}
