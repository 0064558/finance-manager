package com.rodrigs.finance_manager_api.financial_account.dto;

import com.rodrigs.finance_manager_api.financial_account.enums.AccountType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

// DTO para resposta de uma conta financeira
public record FinancialAccountResponseDTO(
        UUID id,
        String name,
        AccountType type,
        BigDecimal initialBalance,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
