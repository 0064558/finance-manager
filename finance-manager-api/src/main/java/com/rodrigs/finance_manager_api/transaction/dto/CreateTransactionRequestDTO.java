package com.rodrigs.finance_manager_api.transaction.dto;

import com.rodrigs.finance_manager_api.shared.enums.TransactionType;
import com.rodrigs.finance_manager_api.shared.validation.TrimmedSize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateTransactionRequestDTO(
        @Schema(description = "Identificador da conta", format = "uuid")
        @NotNull
        UUID accountId,

        @Schema(description = "Identificador da categoria", format = "uuid")
        @NotNull
        UUID categoryId,

        @Schema(description = "Tipo da transação", example = "EXPENSE")
        @NotNull
        TransactionType type,

        @Schema(description = "Valor positivo com no máximo duas casas decimais", example = "125.50", minimum = "0.01")
        @NotNull
        @Digits(integer = 17, fraction = 2) // Máximo de 17 dígitos inteiros e 2 decimais
        @DecimalMin(value = "0.00", inclusive = false) // Maior que zero
        BigDecimal amount,

        @NotNull
        @Schema(description = "Data de ocorrência, não futura", example = "2026-08-15")
        @PastOrPresent
        LocalDate occurredOn,

        @Schema(description = "Descrição opcional, entre 1 e 255 caracteres após normalização", example = "Mercado")
        @TrimmedSize(min = 1, max = 255)
        @Pattern(
                regexp = "(?s).*\\S.*",
                message = "A descrição não pode ser vazia ou conter apenas espaços"
        ) // rejeita texto vazios ou somente espacos em branco
        String description
) {
}
