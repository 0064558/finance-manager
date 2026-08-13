package com.rodrigs.finance_manager_api.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginResponseDTO(
        @Schema(description = "Token JWT para acesso às rotas protegidas", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,
        @Schema(description = "Tipo do token", example = "Bearer")
        String tokenType,
        @Schema(description = "Tempo de expiração do token em segundos", example = "3600")
        long expiresIn,
        @Schema(description = "Dados públicos do usuário autenticado")
        UserResponseDTO user
) {
}
