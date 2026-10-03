package com.chat.server.identity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hs256-hmac";
    private static final UUID USER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expiration", 60_000L);
        ReflectionTestUtils.setField(jwtUtil, "refreshExpiration", 600_000L);
    }

    @Test
    void accessTokenCarriesUserIdAndUsername() {
        String token = jwtUtil.generateToken(USER_ID, "alice");

        assertThat(jwtUtil.validateToken(token)).isTrue();
        assertThat(jwtUtil.getUserIdFromToken(token)).isEqualTo(USER_ID);
        assertThat(jwtUtil.getUsernameFromToken(token)).isEqualTo("alice");
        assertThat(jwtUtil.isRefreshToken(token)).isFalse();
    }

    @Test
    void refreshTokenIsMarkedAsRefresh() {
        String token = jwtUtil.generateRefreshToken(USER_ID);

        assertThat(jwtUtil.validateToken(token)).isTrue();
        assertThat(jwtUtil.isRefreshToken(token)).isTrue();
        assertThat(jwtUtil.getUserIdFromToken(token)).isEqualTo(USER_ID);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        JwtUtil other = new JwtUtil();
        ReflectionTestUtils.setField(other, "secret", "another-secret-key-that-is-long-enough-for-hs256");
        ReflectionTestUtils.setField(other, "expiration", 60_000L);
        ReflectionTestUtils.setField(other, "refreshExpiration", 600_000L);

        assertThat(jwtUtil.validateToken(other.generateToken(USER_ID, "bob"))).isFalse();
    }

    @Test
    void malformedTokenIsRejected() {
        assertThat(jwtUtil.validateToken("not-a-jwt")).isFalse();
        assertThat(jwtUtil.isRefreshToken("not-a-jwt")).isFalse();
    }

    @Test
    void expiredTokenIsRejected() {
        ReflectionTestUtils.setField(jwtUtil, "expiration", -1_000L);
        String expired = jwtUtil.generateToken(USER_ID, "alice");

        assertThat(jwtUtil.validateToken(expired)).isFalse();
    }
}
