package com.rodrigs.finance_manager_api.user.controller;

import com.rodrigs.finance_manager_api.auth.AuthenticatedUser;
import com.rodrigs.finance_manager_api.user.dto.LoginRequestDTO;
import com.rodrigs.finance_manager_api.user.dto.LoginResponseDTO;
import com.rodrigs.finance_manager_api.user.dto.RegisterUserRequestDTO;
import com.rodrigs.finance_manager_api.user.dto.UserResponseDTO;
import com.rodrigs.finance_manager_api.user.service.UserService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Cadastro, login e consulta do usuário autenticado")
public class AuthController {
    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @Operation(
            summary = "Cadastra um usuário",
            description = "Cria um usuário com e-mail normalizado e senha armazenada como hash."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuário cadastrado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "409", description = "E-mail já cadastrado")
    })
    @SecurityRequirements
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponseDTO registerUser(
            @Valid
            @RequestBody RegisterUserRequestDTO request
    ) {
        return userService.registerUser(request);
    }

    @Operation(
            summary = "Autentica um usuário",
            description = "Valida as credenciais e retorna um access token JWT."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login realizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "401", description = "Credenciais inválidas")
    })
    @SecurityRequirements
    @PostMapping("/login")
    public LoginResponseDTO loginUser(@Valid @RequestBody LoginRequestDTO request) {
        return userService.loginUser(request);
    }

    @Operation(
            summary = "Consulta o usuário autenticado",
            description = "Retorna os dados públicos do usuário identificado pelo JWT."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuário autenticado retornado"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    public AuthenticatedUser me(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return authenticatedUser;
    }
}
