package com.devmatch.service;

import com.devmatch.config.AuthProperties;
import com.devmatch.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Session-scoped refresh token store backed by Redis.
 *
 * Key layout:
 *   session:{sessionId}        HASH  { userId, tokenHash, deviceInfo, ip, issuedAt, lastUsedAt, generation }
 *   user_sessions:{userId}     SET   { sessionId, ... }
 *   revoked_session:{sessionId} STRING "1"  (short TTL, for reuse detection after revoke)
 */
@Service
@RequiredArgsConstructor
public class RefreshSessionService {

    private static final String SESSION_KEY_PREFIX = "session:";
    private static final String USER_SESSIONS_KEY_PREFIX = "user_sessions:";
    private static final String REVOKED_SESSION_KEY_PREFIX = "revoked_session:";

    private final StringRedisTemplate redis;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthProperties authProperties;

    public record IssuedSession(String sessionId, String refreshToken, int generation) {}

    public IssuedSession createSession(Long userId, String deviceInfo, String ip) {
        String sessionId = UUID.randomUUID().toString();
        int generation = 1;
        String refreshToken = jwtTokenProvider.generateRefreshToken(userId, sessionId, generation);
        persist(userId, sessionId, refreshToken, generation, deviceInfo, ip, true);
        return new IssuedSession(sessionId, refreshToken, generation);
    }

    /**
     * Rotate refresh token for an existing session. Performs reuse detection:
     *  - session missing -> reject
     *  - token hash mismatch -> reject (wrong token for this session)
     *  - generation mismatch -> REUSE DETECTED, revoke this session
     */
    public IssuedSession rotate(String presentedToken) {
        if (!jwtTokenProvider.validateToken(presentedToken)) {
            throw new InvalidRefreshSessionException("유효하지 않은 Refresh Token입니다");
        }
        Long userId = jwtTokenProvider.getUserIdFromToken(presentedToken);
        String sessionId = jwtTokenProvider.getSessionIdFromToken(presentedToken);
        int presentedGen = jwtTokenProvider.getGenerationFromToken(presentedToken);

        if (sessionId == null) {
            throw new InvalidRefreshSessionException("세션 정보가 없는 Refresh Token입니다");
        }

        if (Boolean.TRUE.equals(redis.hasKey(REVOKED_SESSION_KEY_PREFIX + sessionId))) {
            // Reuse of an already-revoked session -> compromise signal; purge the user's sessions
            revokeAllForUser(userId);
            throw new InvalidRefreshSessionException("이미 revoke된 세션입니다 — 보안상 전체 세션을 종료했습니다");
        }

        Map<Object, Object> session = redis.opsForHash().entries(SESSION_KEY_PREFIX + sessionId);
        if (session.isEmpty()) {
            throw new InvalidRefreshSessionException("세션을 찾을 수 없습니다 (만료되었거나 로그아웃됨)");
        }

        int storedGen = parseInt(session.get("generation"));
        String storedHash = (String) session.get("tokenHash");
        Long storedUserId = parseLong(session.get("userId"));

        if (storedUserId == null || !storedUserId.equals(userId)) {
            throw new InvalidRefreshSessionException("사용자 정보가 일치하지 않습니다");
        }

        String presentedHash = sha256(presentedToken);
        if (storedGen != presentedGen || storedHash == null || !storedHash.equals(presentedHash)) {
            // Reuse detection: revoke this session and mark it for short-term blacklist
            revoke(userId, sessionId);
            throw new InvalidRefreshSessionException("세션 재사용이 감지되어 로그아웃 처리되었습니다");
        }

        int nextGen = storedGen + 1;
        String newToken = jwtTokenProvider.generateRefreshToken(userId, sessionId, nextGen);
        String deviceInfo = (String) session.getOrDefault("deviceInfo", "");
        String ip = (String) session.getOrDefault("ip", "");
        persist(userId, sessionId, newToken, nextGen, deviceInfo, ip, false);
        return new IssuedSession(sessionId, newToken, nextGen);
    }

    public void revoke(Long userId, String sessionId) {
        redis.delete(SESSION_KEY_PREFIX + sessionId);
        if (userId != null) {
            redis.opsForSet().remove(USER_SESSIONS_KEY_PREFIX + userId, sessionId);
        }
        redis.opsForValue().set(
                REVOKED_SESSION_KEY_PREFIX + sessionId,
                "1",
                authProperties.getReuseWindowSeconds(),
                TimeUnit.SECONDS);
    }

    public void revokeByToken(String token) {
        if (token == null || token.isBlank()) return;
        if (!jwtTokenProvider.validateToken(token)) return;
        Long userId = jwtTokenProvider.getUserIdFromToken(token);
        String sessionId = jwtTokenProvider.getSessionIdFromToken(token);
        if (sessionId == null) return;
        revoke(userId, sessionId);
    }

    public void revokeAllForUser(Long userId) {
        if (userId == null) return;
        String setKey = USER_SESSIONS_KEY_PREFIX + userId;
        Set<String> sessionIds = redis.opsForSet().members(setKey);
        if (sessionIds != null) {
            for (String sessionId : sessionIds) {
                redis.delete(SESSION_KEY_PREFIX + sessionId);
                redis.opsForValue().set(
                        REVOKED_SESSION_KEY_PREFIX + sessionId,
                        "1",
                        authProperties.getReuseWindowSeconds(),
                        TimeUnit.SECONDS);
            }
        }
        redis.delete(setKey);
    }

    private void persist(Long userId, String sessionId, String refreshToken, int generation,
                         String deviceInfo, String ip, boolean initial) {
        String sessionKey = SESSION_KEY_PREFIX + sessionId;
        long now = Instant.now().toEpochMilli();
        Map<String, String> fields = Map.of(
                "userId", String.valueOf(userId),
                "tokenHash", sha256(refreshToken),
                "deviceInfo", deviceInfo == null ? "" : deviceInfo,
                "ip", ip == null ? "" : ip,
                "issuedAt", initial ? String.valueOf(now) : stringOrDefault(
                        redis.opsForHash().get(sessionKey, "issuedAt"), String.valueOf(now)),
                "lastUsedAt", String.valueOf(now),
                "generation", String.valueOf(generation)
        );
        redis.opsForHash().putAll(sessionKey, fields);
        redis.expire(sessionKey, jwtTokenProvider.getRefreshTokenExpiration(), TimeUnit.MILLISECONDS);
        redis.opsForSet().add(USER_SESSIONS_KEY_PREFIX + userId, sessionId);
        redis.expire(USER_SESSIONS_KEY_PREFIX + userId,
                jwtTokenProvider.getRefreshTokenExpiration(), TimeUnit.MILLISECONDS);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static int parseInt(Object v) {
        if (v == null) return 0;
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return 0; }
    }

    private static Long parseLong(Object v) {
        if (v == null) return null;
        try { return Long.parseLong(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    private static String stringOrDefault(Object v, String fallback) {
        return v == null ? fallback : v.toString();
    }

    public static class InvalidRefreshSessionException extends RuntimeException {
        public InvalidRefreshSessionException(String message) { super(message); }
    }
}
