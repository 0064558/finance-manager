package com.rodrigs.finance_manager_api.category.dto;

import com.rodrigs.finance_manager_api.shared.enums.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequestDTO(
        @Schema(
                description = "Nome da categoria",
                example = "Alimentação",
                minLength = 2,
                maxLength = 80
        )
        @NotBlank
        @Size(min = 2, max = 80)
        String name,

        @Schema(
                description = "Tipo da categoria",
                example = "EXPENSE"
        )
        @NotNull
        TransactionType transactionType
) {
}
