package com.devmatch.service.ai;

import com.devmatch.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AiReviewRateLimitInterceptor implements HandlerInterceptor {

    private final AiReviewRateLimitService rateLimitService;

    public AiReviewRateLimitInterceptor(AiReviewRateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userKey = null;
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            userKey = String.valueOf(userDetails.getUserId());
        }

        rateLimitService.check(userKey, clientIp(request));
        return true;
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }
}
