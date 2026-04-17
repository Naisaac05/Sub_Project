package com.devmatch.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.auth")
@Getter @Setter
public class AuthProperties {

    private RefreshCookie refreshCookie = new RefreshCookie();
    private long reuseWindowSeconds = 300;

    @Getter @Setter
    public static class RefreshCookie {
        private String name = "refresh_token";
        private String path = "/api/auth";
        private boolean secure = false;
        private String sameSite = "Strict";
    }
}
