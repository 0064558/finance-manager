package com.rodrigs.finance_manager_api.category.dto;

import com.rodrigs.finance_manager_api.shared.enums.TransactionType;
import com.rodrigs.finance_manager_api.shared.validation.TrimmedSize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateCategoryRequestDTO(
        @Schema(
                description = "Nome da categoria",
                example = "Alimentação",
                minLength = 2,
                maxLength = 80
        )
        @NotBlank
        @TrimmedSize(min = 2, max = 80)
        String name,

        @Schema(
                description = "Tipo da categoria",
                example = "EXPENSE"
        )
        @NotNull
        TransactionType transactionType
) {
}
