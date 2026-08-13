package com.rodrigs.finance_manager_api.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequestDTO(
    @Schema(description = "E-mail cadastrado", example = "rodrigo@email.com", maxLength = 254)
    @NotBlank
    @Email
    @Size(max = 254)
    String email,

    @Schema(description = "Senha do usuário", example = "senha1234", format = "password", maxLength = 72)
    @NotBlank
    @Size(max = 72)
    String password
) {
}
