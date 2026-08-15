package com.rodrigs.finance_manager_api.transaction.controller;

import com.rodrigs.finance_manager_api.auth.AuthenticatedUser;
import com.rodrigs.finance_manager_api.shared.enums.TransactionType;
import com.rodrigs.finance_manager_api.transaction.dto.CreateTransactionRequestDTO;
import com.rodrigs.finance_manager_api.transaction.dto.TransactionResponseDTO;
import com.rodrigs.finance_manager_api.transaction.dto.UpdateTransactionRequestDTO;
import com.rodrigs.finance_manager_api.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {
    private final TransactionService transactionService;


    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponseDTO createTransaction(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody CreateTransactionRequestDTO requestDTO
            ) {
        return transactionService.createTransaction(authenticatedUser.id(), requestDTO);
    }

    @GetMapping
    public Page<TransactionResponseDTO> findAllTransactions(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(value = "startDate", required = false) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) LocalDate endDate,
            @RequestParam(value = "type", required = false) TransactionType type,
            @RequestParam(value = "accountId", required = false) UUID accountId,
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

    @GetMapping("/{transactionId}")
    public TransactionResponseDTO findTransactionById(
            @PathVariable UUID transactionId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return transactionService.findTransactionById(transactionId, authenticatedUser.id());
    }

    @PutMapping("/{transactionId}")
    public TransactionResponseDTO updateTransaction(
            @PathVariable UUID transactionId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody UpdateTransactionRequestDTO requestDTO
    ) {
        return transactionService.updateTransaction(transactionId, authenticatedUser.id(), requestDTO);
    }
}
