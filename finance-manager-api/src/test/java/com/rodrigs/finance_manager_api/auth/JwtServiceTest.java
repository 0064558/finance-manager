package com.rodrigs.finance_manager_api.auth;

import com.rodrigs.finance_manager_api.config.JwtProperties;
import com.rodrigs.finance_manager_api.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String ISSUER = "finance-manager-api-test";
    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new JwtProperties(ISSUER, 3600, SECRET));
        jwtService.initializeSecretKey();
    }

    @Test
    void shouldGenerateAndValidateToken() {
        User user = userWithId();

        String token = jwtService.generateAccessToken(user);

        assertThat(token).isNotBlank();
        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.extractUserId(token)).isEqualTo(user.getId());
    }

    @Test
    void shouldRejectMalformedToken() {
        assertThat(jwtService.isTokenValid("token-invalido")).isFalse();
    }

    @Test
    void shouldRejectTokenSignedWithAnotherSecret() {
        User user = userWithId();
        JwtService otherJwtService = new JwtService(
                new JwtProperties(ISSUER, 3600, "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789")
        );
        otherJwtService.initializeSecretKey();

        String token = otherJwtService.generateAccessToken(user);

        assertThat(jwtService.isTokenValid(token)).isFalse();
    }

    @Test
    void shouldRejectExpiredToken() {
        User user = userWithId();
        JwtService expiredJwtService = new JwtService(new JwtProperties(ISSUER, -1, SECRET));
        expiredJwtService.initializeSecretKey();

        String token = expiredJwtService.generateAccessToken(user);

        assertThat(jwtService.isTokenValid(token)).isFalse();
    }

    private User userWithId() {
        User user = new User("Rodrigo", "rodrigo@email.com", "hashed-password");
        UUID id = UUID.randomUUID();

        try {
            Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not prepare test user", exception);
        }

        return user;
    }
}
