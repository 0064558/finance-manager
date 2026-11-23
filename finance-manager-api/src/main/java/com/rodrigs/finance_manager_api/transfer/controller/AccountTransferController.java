package com.rodrigs.finance_manager_api.transfer.controller;

import com.rodrigs.finance_manager_api.auth.AuthenticatedUser;
import com.rodrigs.finance_manager_api.transfer.dto.AccountTransferResponseDTO;
import com.rodrigs.finance_manager_api.transfer.dto.CreateAccountTransferRequestDTO;
import com.rodrigs.finance_manager_api.transfer.service.AccountTransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "Transfers", description = "Transferências entre contas do usuário autenticado")
@SecurityRequirement(name = "bearerAuth")
public class AccountTransferController {

    private final AccountTransferService transfers;

    public AccountTransferController(AccountTransferService transfers) {
        this.transfers = transfers;
    }

    @Operation(summary = "Registra uma transferência entre contas próprias",
            description = "Debita a origem e credita o destino atomicamente. Reenvios com a mesma Idempotency-Key e os mesmos dados retornam a transferência existente.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Transferência registrada"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou contas iguais"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "404", description = "Conta não encontrada para o usuário"),
            @ApiResponse(responseCode = "409", description = "Chave de idempotência reutilizada com dados diferentes")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountTransferResponseDTO create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateAccountTransferRequestDTO request,
            @Parameter(description = "Chave por operação, reutilizada em reenvios, com até 200 caracteres")
            @RequestHeader(value = "Idempotency-Key", required = false) String key
    ) {
        return transfers.create(user.id(), request, key);
    }

    @Operation(summary = "Lista o histórico de transferências entre contas próprias")
    @GetMapping
    public Page<AccountTransferResponseDTO> findAll(
            @AuthenticationPrincipal AuthenticatedUser user,
            @ParameterObject @PageableDefault(size = 10, sort = {"occurredOn", "createdAt", "id"},
                    direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return transfers.findAll(user.id(), pageable);
    }
}
