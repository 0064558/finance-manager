package com.rodrigs.finance_manager_api.category.dto;

import com.rodrigs.finance_manager_api.shared.enums.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CategoryResponseDTO(
        @Schema(description = "Identificador da categoria", format = "uuid")
        UUID id,
        @Schema(description = "Nome normalizado da categoria", example = "Alimentação")
        String name,
        @Schema(description = "Tipo da categoria", example = "EXPENSE")
        TransactionType transactionType,
        @Schema(description = "Data e hora de criação")
        OffsetDateTime createdAt,
        @Schema(description = "Data e hora da última atualização")
        OffsetDateTime updatedAt
) {
}
