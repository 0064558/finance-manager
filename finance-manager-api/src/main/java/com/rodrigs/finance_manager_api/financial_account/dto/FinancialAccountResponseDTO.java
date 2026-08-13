package com.rodrigs.finance_manager_api.financial_account.dto;

import com.rodrigs.finance_manager_api.financial_account.enums.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

// DTO para resposta de uma conta financeira
public record FinancialAccountResponseDTO(
        @Schema(description = "Identificador da conta", format = "uuid")
        UUID id,
        @Schema(description = "Nome da conta", example = "Conta corrente")
        String name,
        @Schema(description = "Tipo da conta", example = "CHECKING")
        AccountType type,
        @Schema(description = "Saldo inicial", example = "1500.00")
        BigDecimal initialBalance,
        @Schema(description = "Data e hora de criação")
        OffsetDateTime createdAt,
        @Schema(description = "Data e hora da última atualização")
        OffsetDateTime updatedAt
) {
}
