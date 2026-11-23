package com.rodrigs.finance_manager_api.transfer.dto;

import com.rodrigs.finance_manager_api.shared.validation.TrimmedSize;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateAccountTransferRequestDTO(
        @NotNull UUID sourceAccountId,
        @NotNull UUID destinationAccountId,
        @NotNull @Digits(integer = 17, fraction = 2) @DecimalMin("0.01") BigDecimal amount,
        @NotNull @PastOrPresent LocalDate occurredOn,
        @TrimmedSize(min = 1, max = 255)
        @Pattern(regexp = "(?s).*\\S.*", message = "A descrição não pode ser vazia ou conter apenas espaços")
        String description
) {
}
