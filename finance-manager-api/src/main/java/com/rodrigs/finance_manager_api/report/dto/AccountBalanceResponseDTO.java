package com.rodrigs.finance_manager_api.report.dto;

import com.rodrigs.finance_manager_api.financial_account.enums.AccountType;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountBalanceResponseDTO(
        UUID accountId,
        String accountName,
        AccountType accountType,
        BigDecimal balance
) {
}
