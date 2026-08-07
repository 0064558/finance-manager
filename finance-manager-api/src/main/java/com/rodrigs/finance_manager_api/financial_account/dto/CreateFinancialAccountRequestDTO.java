package com.rodrigs.finance_manager_api.financial_account.dto;

import com.rodrigs.finance_manager_api.financial_account.enums.AccountType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateFinancialAccountRequestDTO(
        @NotBlank
        @Size(min = 2, max = 100)
        String name,

        @NotNull
        AccountType type,

        @NotNull
        @Digits(integer = 17, fraction = 2)
        BigDecimal initialBalance
) {
}
