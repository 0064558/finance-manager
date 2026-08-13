package com.rodrigs.finance_manager_api.transaction.dto;

import com.rodrigs.finance_manager_api.shared.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransactionResponseDTO(
        UUID id,
        UUID accountId,
        UUID categoryId,
        TransactionType type,
        BigDecimal amount,
        LocalDate occurredOn,
        String description,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
