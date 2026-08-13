package com.rodrigs.finance_manager_api.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponseDTO(
        @Schema(description = "Identificador do usuário", format = "uuid")
        UUID id,
        @Schema(description = "Nome de exibição", example = "Rodrigo")
        String name,
        @Schema(description = "E-mail normalizado", example = "rodrigo@email.com")
        String email,
        @Schema(description = "Data e hora de criação")
        OffsetDateTime createdAt
) {
}
