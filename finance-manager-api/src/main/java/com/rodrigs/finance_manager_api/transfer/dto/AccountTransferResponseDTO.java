package com.rodrigs.finance_manager_api.transfer.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AccountTransferResponseDTO(
        UUID id,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        LocalDate occurredOn,
        String description,
        OffsetDateTime createdAt
) {
}
