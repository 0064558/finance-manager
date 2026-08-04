package com.rodrigs.finance_manager_api.user.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponseDTO(
        UUID id,
        String name,
        String email,
        OffsetDateTime createdAt
) {
}
