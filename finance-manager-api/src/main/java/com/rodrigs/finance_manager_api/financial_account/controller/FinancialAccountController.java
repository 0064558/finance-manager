package com.rodrigs.finance_manager_api.financial_account.controller;

import com.rodrigs.finance_manager_api.auth.AuthenticatedUser;
import com.rodrigs.finance_manager_api.financial_account.dto.CreateFinancialAccountRequestDTO;
import com.rodrigs.finance_manager_api.financial_account.dto.FinancialAccountResponseDTO;
import com.rodrigs.finance_manager_api.financial_account.service.FinancialAccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/financial-accounts")
public class FinancialAccountController {
    private final FinancialAccountService accountService;

    public FinancialAccountController(FinancialAccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FinancialAccountResponseDTO createAccount(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser, // pega o usuario que esta autenticado
            @Valid
            @RequestBody CreateFinancialAccountRequestDTO request) {
        return accountService.createFinancialAccount(authenticatedUser.id(), request);
    }

    @GetMapping
    public List<FinancialAccountResponseDTO> findAllAccounts(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return accountService.findAllAccounts(authenticatedUser.id());
    }

    @GetMapping("/{accountId}")
    public FinancialAccountResponseDTO findAccountById(@PathVariable UUID accountId, @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        return accountService.findAccountById(accountId, authenticatedUser.id());
    }

}
