package com.rodrigs.finance_manager_api.financial_account.dto;

import com.rodrigs.finance_manager_api.financial_account.enums.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

// DTO para criar uma conta financeira
public record CreateFinancialAccountRequestDTO(
        @Schema(description = "Nome identificador da conta", example = "Conta corrente", minLength = 2, maxLength = 100)
        @NotBlank
        @Size(min = 2, max = 100)
        String name,

        @Schema(description = "Tipo da conta", example = "CHECKING")
        @NotNull
        AccountType type,

        @Schema(description = "Saldo inicial, podendo ser negativo, zero ou positivo", example = "1500.00", minimum = "-99999999999999999.99")
        @NotNull
        @Digits(integer = 17, fraction = 2)
        BigDecimal initialBalance
) {
}
