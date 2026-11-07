package com.rodrigs.finance_manager_api.transaction.controller;

import com.rodrigs.finance_manager_api.auth.AuthenticatedUser;
import com.rodrigs.finance_manager_api.transaction.dto.TransactionSuggestionResponseDTO;
import com.rodrigs.finance_manager_api.transaction.service.TransactionSuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions/suggestions")
@SecurityRequirement(name = "bearerAuth")
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "google-genai")
public class TransactionSuggestionController {

    private final TransactionSuggestionService suggestionService;

    public TransactionSuggestionController(TransactionSuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    @Operation(summary = "Gera uma sugestão de transação estruturada para revisão")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public TransactionSuggestionResponseDTO suggest(@AuthenticationPrincipal AuthenticatedUser user,
                                                     @Valid @RequestBody SuggestionRequest request) {
        return suggestionService.suggest(user.id(), request.description());
    }

    public record SuggestionRequest(@NotBlank @Size(max = 500) String description) {
    }
}
