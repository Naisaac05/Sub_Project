package com.devmatch.controller;

import com.devmatch.config.AuthProperties;
import com.devmatch.dto.auth.LoginRequest;
import com.devmatch.dto.auth.SignupRequest;
import com.devmatch.dto.auth.TokenResponse;
import com.devmatch.dto.common.ApiResponse;
import com.devmatch.dto.user.UserResponse;
import com.devmatch.security.JwtTokenProvider;
import com.devmatch.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final AuthProperties authProperties;
    private final JwtTokenProvider jwtTokenProvider;

    @Operation(summary = "회원가입")
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<UserResponse>> signup(@Valid @RequestBody SignupRequest request) {
        UserResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("회원가입이 완료되었습니다", response));
    }

    @Operation(summary = "로그인")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String deviceInfo = header(httpRequest, "User-Agent");
        String ip = clientIp(httpRequest);
        log.info("Auth API: login request received: email={}, deviceInfo={}, ip={}", request.getEmail(), deviceInfo, ip);

        AuthService.AuthTokens tokens = authService.login(request, deviceInfo, ip);

        log.info("Auth API: login success for email={}", request.getEmail());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(tokens.refreshToken()).toString())
                .body(ApiResponse.success("로그인 성공", TokenResponse.of(tokens.accessToken())));
    }

    @Operation(summary = "토큰 갱신")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(HttpServletRequest httpRequest) {
        String presented = readRefreshCookie(httpRequest);
        AuthService.AuthTokens tokens = authService.refresh(presented);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(tokens.refreshToken()).toString())
                .body(ApiResponse.success("토큰이 갱신되었습니다", TokenResponse.of(tokens.accessToken())));
    }

    @Operation(summary = "비밀번호 변경 (강제 변경 포함)")
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal com.devmatch.security.CustomUserDetails user,
            @Valid @RequestBody com.devmatch.dto.auth.PasswordChangeRequest request) {
        authService.changePassword(user.getUserId(), request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success("비밀번호가 변경되었습니다", null));
    }

    @Operation(summary = "로그아웃")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest httpRequest) {
        String presented = readRefreshCookie(httpRequest);
        authService.logout(presented);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .body(ApiResponse.success("로그아웃 되었습니다", null));
    }

    private ResponseCookie buildRefreshCookie(String value) {
        AuthProperties.RefreshCookie cfg = authProperties.getRefreshCookie();
        return ResponseCookie.from(cfg.getName(), value)
                .httpOnly(true)
                .secure(cfg.isSecure())
                .sameSite(cfg.getSameSite())
                .path(cfg.getPath())
                .maxAge(Duration.ofMillis(jwtTokenProvider.getRefreshTokenExpiration()))
                .build();
    }

    private ResponseCookie clearRefreshCookie() {
        AuthProperties.RefreshCookie cfg = authProperties.getRefreshCookie();
        return ResponseCookie.from(cfg.getName(), "")
                .httpOnly(true)
                .secure(cfg.isSecure())
                .sameSite(cfg.getSameSite())
                .path(cfg.getPath())
                .maxAge(0)
                .build();
    }

    private String readRefreshCookie(HttpServletRequest request) {
        String name = authProperties.getRefreshCookie().getName();
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    private static String header(HttpServletRequest req, String name) {
        String v = req.getHeader(name);
        return v == null ? "" : v;
    }

    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return req.getRemoteAddr();
    }
}
