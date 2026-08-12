package com.rodrigs.finance_manager_api.category.dto;

import com.rodrigs.finance_manager_api.shared.enums.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateCategoryRequestDTO(
        @NotBlank
        @Size(min = 2, max = 80)
        String name,

        @NotNull
        TransactionType transactionType
) {
}
