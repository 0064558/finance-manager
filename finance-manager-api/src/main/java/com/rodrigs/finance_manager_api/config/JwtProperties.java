package com.rodrigs.finance_manager_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Record para armazenar as propriedades de configuração do JWT, como emissor, tempo de expiração e segredo.
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String issuer,
        long expirationSeconds,
        String secret
) {

}
