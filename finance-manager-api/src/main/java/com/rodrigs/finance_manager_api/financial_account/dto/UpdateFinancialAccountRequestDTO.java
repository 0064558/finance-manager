package com.rodrigs.finance_manager_api.financial_account.dto;

import com.rodrigs.finance_manager_api.financial_account.enums.AccountType;
import com.rodrigs.finance_manager_api.shared.validation.TrimmedSize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

// DTO para atualizar uam conta
public record UpdateFinancialAccountRequestDTO (
        @Schema(description = "Nome identificador da conta", example = "Conta corrente principal", minLength = 2, maxLength = 100)
        @NotBlank
        @TrimmedSize(min = 2, max = 100)
        String name,

        @Schema(description = "Tipo da conta", example = "CHECKING")
        @NotNull
        AccountType type,

        @Schema(description = "Saldo inicial, podendo ser negativo, zero ou positivo", example = "1700.00", minimum = "-99999999999999999.99")
        @NotNull
        @Digits(integer = 17, fraction = 2)
        BigDecimal initialBalance
) {

}
