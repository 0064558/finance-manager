package com.rodrigs.finance_manager_api.user.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateOnboardingRequestDTO(
        @NotNull
        @Min(0) // onboardingVersion deve ser um número inteiro não negativo
        Integer onboardingVersion
) {
}
