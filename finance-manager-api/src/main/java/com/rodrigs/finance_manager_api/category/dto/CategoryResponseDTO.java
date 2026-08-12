package com.rodrigs.finance_manager_api.category.dto;

import com.rodrigs.finance_manager_api.shared.enums.TransactionType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CategoryResponseDTO(
        UUID id,
        String name,
        TransactionType transactionType,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
