package com.rodrigs.finance_manager_api.transaction.controller;

import com.rodrigs.finance_manager_api.auth.AuthenticatedUser;
import com.rodrigs.finance_manager_api.transaction.controller.TransactionSuggestionController.SuggestionRequest;
import com.rodrigs.finance_manager_api.transaction.service.TransactionChatHistory;
import com.rodrigs.finance_manager_api.transaction.service.TransactionSuggestionService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions/chat")
@SecurityRequirement(name = "bearerAuth")
public class TransactionChatController {
    private final TransactionChatHistory history;
    private final ObjectProvider<TransactionSuggestionService> suggestions;

    public TransactionChatController(TransactionChatHistory history, ObjectProvider<TransactionSuggestionService> suggestions) {
        this.history = history;
        this.suggestions = suggestions;
    }

    @GetMapping
    public ResponseEntity<List<TransactionChatHistory.Entry>> history(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(history.findByUser(user.id()));
    }

    @PostMapping
    public ResponseEntity<TransactionChatHistory.Entry> send(@AuthenticationPrincipal AuthenticatedUser user,
                                                              @Valid @RequestBody SuggestionRequest request) {
        var suggestionService = suggestions.getIfAvailable();
        if (suggestionService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        String prompt = request.description().strip();
        var response = suggestionService.suggest(user.id(), prompt);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(history.save(user.id(), prompt, response));
    }
}
