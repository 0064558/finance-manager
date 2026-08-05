package com.rodrigs.finance_manager_api.auth;

import com.rodrigs.finance_manager_api.config.JwtProperties;
import com.rodrigs.finance_manager_api.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
// This service class is responsible for generating and validating JWT tokens for user authentication.
public class JwtService {

    private final JwtProperties jwtProperties;

    // The secret key used for signing and verifying JWT tokens. It is initialized in the initializeSecretKey() method after the JwtProperties are injected.
    private SecretKey secretKey;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @PostConstruct
    void initializeSecretKey() {
        if (jwtProperties.secret() == null || jwtProperties.secret().isBlank()) {
            throw new IllegalStateException("JWT secret must be configured");
        }

        // Convert the secret string to a SecretKey using the HMAC SHA algorithm. The secret is converted to bytes using UTF-8 encoding.
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generates a new access token for the given user.
     *
     * @param user the user for whom to generate the token
     * @return the generated access token
     */
    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(jwtProperties.expirationSeconds());

        return Jwts.builder()
                .issuer(jwtProperties.issuer())
                .subject(user.getId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim("email", user.getEmail())
                .signWith(secretKey)
                .compact();
    }

    /**
     * Extracts the user ID from the given JWT token.
     *
     * @param token the JWT token from which to extract the user ID
     * @return the extracted user ID
     */
    public UUID extractUserId(String token) {
        Claims claims = parseClaims(token);

        return UUID.fromString(claims.getSubject());
    }

    /**
     * Checks if the given JWT token is valid.
     *
     * @param token the JWT token to validate
     * @return true if the token is valid, false otherwise
     */
    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * Parses the claims from the given JWT token.
     *
     * @param token the JWT token from which to parse the claims
     * @return the parsed claims
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(jwtProperties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}