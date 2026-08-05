package com.rodrigs.finance_manager_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String issuer,
        long expirationSeconds,
        String secret
) {

}
