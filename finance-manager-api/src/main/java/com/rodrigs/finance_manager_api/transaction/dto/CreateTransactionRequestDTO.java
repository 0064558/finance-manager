package com.rodrigs.finance_manager_api.transaction.dto;

import com.rodrigs.finance_manager_api.shared.enums.TransactionType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateTransactionRequestDTO(
        @NotNull
        UUID accountId,

        @NotNull
        UUID categoryId,

        @NotNull
        TransactionType type,

        @NotNull
        @Digits(integer = 17, fraction = 2) // Máximo de 17 dígitos inteiros e 2 decimais
        @DecimalMin(value = "0.00", inclusive = false) // Maior que zero
        BigDecimal amount,

        @NotNull
        @PastOrPresent
        LocalDate occurredOn,

        @Size(max = 255)
        @Pattern(
                regexp = "(?s).*\\S.*",
                message = "A descrição não pode ser vazia ou conter apenas espaços"
        ) // rejeita texto vazios ou somente espacos em branco
        String description
) {
}
