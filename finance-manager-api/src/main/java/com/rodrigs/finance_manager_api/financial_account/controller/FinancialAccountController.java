package com.rodrigs.finance_manager_api.financial_account.controller;

import com.rodrigs.finance_manager_api.auth.AuthenticatedUser;
import com.rodrigs.finance_manager_api.financial_account.dto.CreateFinancialAccountRequestDTO;
import com.rodrigs.finance_manager_api.financial_account.dto.FinancialAccountResponseDTO;
import com.rodrigs.finance_manager_api.financial_account.dto.UpdateFinancialAccountRequestDTO;
import com.rodrigs.finance_manager_api.financial_account.service.FinancialAccountService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/financial-accounts")
@Tag(name = "Financial Accounts", description = "Gerenciamento das contas financeiras do usuário autenticado")
@SecurityRequirement(name = "bearerAuth")
public class FinancialAccountController {
    private final FinancialAccountService accountService;

    public FinancialAccountController(FinancialAccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(
            summary = "Cria uma conta financeira",
            description = "Cria uma conta financeira para o usuário autenticado."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Conta criada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FinancialAccountResponseDTO createAccount(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser, // pega o usuario que esta autenticado
            @Valid
            @RequestBody CreateFinancialAccountRequestDTO request) {
        return accountService.createFinancialAccount(authenticatedUser.id(), request);
    }

    @Operation(
            summary = "Lista as contas financeiras",
            description = "Retorna somente as contas pertencentes ao usuário autenticado."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contas retornadas com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido")
    })
    @GetMapping
    public List<FinancialAccountResponseDTO> findAllAccounts(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return accountService.findAllAccounts(authenticatedUser.id());
    }

    @Operation(
            summary = "Busca uma conta financeira por ID",
            description = "Consulta uma conta pertencente ao usuário autenticado."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conta encontrada"),
            @ApiResponse(responseCode = "400", description = "UUID inválido"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "404", description = "Conta não encontrada")
    })
    @GetMapping("/{accountId}")
    public FinancialAccountResponseDTO findAccountById(
            @Parameter(
                    description = "ID da conta financeira",
                    example = "99d6a5b7-900d-4221-aef6-3da6b865b37c"
            )
            @PathVariable UUID accountId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return accountService.findAccountById(accountId, authenticatedUser.id());
    }

    @Operation(
            summary = "Atualiza uma conta financeira",
            description = "Atualiza nome, tipo e saldo inicial. O saldo inicial não pode ser alterado após o primeiro lançamento."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conta atualizada"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "404", description = "Conta não encontrada"),
            @ApiResponse(responseCode = "409", description = "Conta possui transações e a alteração é proibida")
    })
    @PutMapping("/{accountId}")
    public FinancialAccountResponseDTO updateAccount(
            @Parameter(
                    description = "ID da conta financeira",
                    example = "99d6a5b7-900d-4221-aef6-3da6b865b37c"
            )
            @PathVariable UUID accountId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody UpdateFinancialAccountRequestDTO request
    ) {
        return accountService.updateFinancialAccount(accountId, authenticatedUser.id(), request);
    }

    @Operation(
            summary = "Exclui uma conta financeira",
            description = "Exclui uma conta somente quando ela não possui transações vinculadas."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Conta excluída"),
            @ApiResponse(responseCode = "400", description = "UUID inválido"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "404", description = "Conta não encontrada"),
            @ApiResponse(responseCode = "409", description = "Conta possui transações")
    })
    @DeleteMapping("/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(
            @Parameter(
                    description = "ID da conta financeira",
                    example = "99d6a5b7-900d-4221-aef6-3da6b865b37c"
            )
            @PathVariable UUID accountId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        accountService.deleteFinancialAccount(accountId, authenticatedUser.id());
    }

}
