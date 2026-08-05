package com.rodrigs.finance_manager_api.user.dto;

public record LoginResponseDTO(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponseDTO user
) {
}
