package com.rodrigs.finance_manager_api.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterUserRequestDTO(
    @Schema(description = "Nome de exibição", example = "Rodrigo", maxLength = 100)
    @NotBlank
    @Size(max = 100)
    String name,

    @Schema(description = "E-mail do usuário", example = "rodrigo@email.com", maxLength = 254)
    @NotBlank
    @Email
    @Size(max = 254)
    String email,

    @Schema(description = "Senha em texto puro usada somente para autenticação", example = "senha1234", format = "password", minLength = 8, maxLength = 72)
    @NotBlank
    @Size(min = 8, max = 72)
    String password
)
{

}
