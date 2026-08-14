package com.rodrigs.finance_manager_api.transaction.controller;

import com.rodrigs.finance_manager_api.auth.AuthenticatedUser;
import com.rodrigs.finance_manager_api.transaction.dto.CreateTransactionRequestDTO;
import com.rodrigs.finance_manager_api.transaction.dto.TransactionResponseDTO;
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
            @PageableDefault(size = 20, sort = {"occurredOn", "createdAt"}, direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return transactionService.findAllTransactions(authenticatedUser.id(), pageable);
    }
}
