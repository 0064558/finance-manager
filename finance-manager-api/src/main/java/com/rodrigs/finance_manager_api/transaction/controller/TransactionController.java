package com.rodrigs.finance_manager_api.transaction.controller;

import com.rodrigs.finance_manager_api.auth.AuthenticatedUser;
import com.rodrigs.finance_manager_api.shared.enums.TransactionType;
import com.rodrigs.finance_manager_api.transaction.dto.CreateTransactionRequestDTO;
import com.rodrigs.finance_manager_api.transaction.dto.TransactionResponseDTO;
import com.rodrigs.finance_manager_api.transaction.dto.UpdateTransactionRequestDTO;
import com.rodrigs.finance_manager_api.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transactions", description = "Gerenciamento de transações financeiras do usuário autenticado")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {
    private final TransactionService transactionService;


    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Operation(
            summary = "Cria uma transação",
            description = "Registra uma receita ou despesa em uma conta e categoria pertencentes ao usuário autenticado."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Transação criada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos, valor não positivo ou data futura"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "404", description = "Conta, categoria ou usuário não encontrado"),
            @ApiResponse(responseCode = "409", description = "Tipo da transação incompatível com a categoria")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponseDTO createTransaction(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody CreateTransactionRequestDTO requestDTO
            ) {
        return transactionService.createTransaction(authenticatedUser.id(), requestDTO);
    }

    @Operation(
            summary = "Lista transações",
            description = "Lista somente as transações do usuário autenticado. A paginação é zero-based e a ordenação padrão é occurredOn decrescente, seguida de createdAt decrescente. As datas do período são inclusivas."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transações retornadas com sucesso"),
            @ApiResponse(responseCode = "400", description = "Parâmetros inválidos ou intervalo de datas inválido"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "404", description = "Conta ou categoria do filtro não encontrada")
    })
    @GetMapping
    public Page<TransactionResponseDTO> findAllTransactions(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Parameter(description = "Data inicial do período, inclusiva", example = "2026-08-01")
            @RequestParam(value = "startDate", required = false) LocalDate startDate,
            @Parameter(description = "Data final do período, inclusiva", example = "2026-08-31")
            @RequestParam(value = "endDate", required = false) LocalDate endDate,
            @Parameter(description = "Filtra pelo tipo da transação", example = "EXPENSE")
            @RequestParam(value = "type", required = false) TransactionType type,
            @Parameter(description = "Filtra por uma conta pertencente ao usuário", example = "8eb438af-1c4e-4395-8385-15ed32a80a60")
            @RequestParam(value = "accountId", required = false) UUID accountId,
            @Parameter(description = "Filtra por uma categoria pertencente ao usuário", example = "6e95f378-4b1e-4bb4-be15-5cf2bf619812")
            @RequestParam(value = "categoryId", required = false) UUID categoryId,
            @PageableDefault(size = 20, sort = {"occurredOn", "createdAt"}, direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return transactionService.findAllTransactions(
                authenticatedUser.id(),
                startDate,
                endDate,
                type,
                accountId,
                categoryId,
                pageable
        );
    }

    @Operation(
            summary = "Busca uma transação por ID",
            description = "Consulta uma transação pertencente ao usuário autenticado."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transação encontrada"),
            @ApiResponse(responseCode = "400", description = "UUID inválido"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "404", description = "Transação não encontrada")
    })
    @GetMapping("/{transactionId}")
    public TransactionResponseDTO findTransactionById(
            @Parameter(description = "ID da transação", example = "d511327d-4e93-460d-a3b0-1be08037fc3d")
            @PathVariable UUID transactionId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return transactionService.findTransactionById(transactionId, authenticatedUser.id());
    }

    @Operation(
            summary = "Atualiza uma transação",
            description = "Atualiza integralmente uma transação, revalidando conta, categoria, tipo, valor e data."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transação atualizada"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos, valor não positivo ou data futura"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "404", description = "Transação, conta ou categoria não encontrada"),
            @ApiResponse(responseCode = "409", description = "Tipo da transação incompatível com a categoria")
    })
    @PutMapping("/{transactionId}")
    public TransactionResponseDTO updateTransaction(
            @Parameter(description = "ID da transação", example = "d511327d-4e93-460d-a3b0-1be08037fc3d")
            @PathVariable UUID transactionId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody UpdateTransactionRequestDTO requestDTO
    ) {
        return transactionService.updateTransaction(transactionId, authenticatedUser.id(), requestDTO);
    }

    @Operation(
            summary = "Exclui uma transação",
            description = "Exclui fisicamente uma transação pertencente ao usuário autenticado. Uma nova exclusão do mesmo ID retorna não encontrado."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Transação excluída"),
            @ApiResponse(responseCode = "400", description = "UUID inválido"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou inválido"),
            @ApiResponse(responseCode = "404", description = "Transação não encontrada")
    })
    @DeleteMapping("/{transactionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTransaction(
            @Parameter(description = "ID da transação", example = "d511327d-4e93-460d-a3b0-1be08037fc3d")
            @PathVariable UUID transactionId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        transactionService.deleteTransaction(transactionId, authenticatedUser.id());
    }
}
