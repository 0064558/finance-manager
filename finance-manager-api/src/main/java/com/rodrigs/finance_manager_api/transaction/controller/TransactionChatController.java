package com.rodrigs.finance_manager_api.transaction.controller;

import com.rodrigs.finance_manager_api.auth.AuthenticatedUser;
import com.rodrigs.finance_manager_api.shared.exception.TransactionChatException;
import com.rodrigs.finance_manager_api.transaction.service.TransactionChatHistory;
import com.rodrigs.finance_manager_api.transaction.service.TransactionSuggestionService;
import com.rodrigs.finance_manager_api.transaction.service.TransactionSuggestionService.ChatMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static com.rodrigs.finance_manager_api.shared.exception.TransactionChatException.Reason.DRAFT_UNAVAILABLE;
import static com.rodrigs.finance_manager_api.shared.exception.TransactionChatException.Reason.INVALID_TIME_ZONE;

@RestController
@RequestMapping("/api/v1/transactions/chat")
@SecurityRequirement(name = "bearerAuth")
public class TransactionChatController {
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Sao_Paulo");

    private final TransactionChatHistory history;
    private final ObjectProvider<TransactionSuggestionService> suggestions;

    public TransactionChatController(TransactionChatHistory history, ObjectProvider<TransactionSuggestionService> suggestions) {
        this.history = history;
        this.suggestions = suggestions;
    }

    @GetMapping
    @Operation(summary = "Lista as mensagens do chat e o estado de seus rascunhos")
    public ResponseEntity<List<TransactionChatHistory.Entry>> history(@AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(history.findByUser(user.id()));
    }

    @PostMapping
    @Operation(summary = "Interpreta uma mensagem nova ou uma resposta do chat")
    public ResponseEntity<TransactionChatHistory.Entry> send(@AuthenticationPrincipal AuthenticatedUser user,
                                                              @Valid @RequestBody ChatRequest request) {
        long revision = history.revision(user.id());
        String prompt = request.description().strip();
        var replay = history.findReplay(user.id(), request.requestId(), prompt, request.replyTo());
        if (replay.isPresent()) return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(replay.get());

        var current = new ChatMessage(prompt, LocalDate.now(zone(request.timeZone())));
        List<ChatMessage> context;
        try {
            context = history.context(user.id(), request.replyTo(), current);
        } catch (TransactionChatException exception) {
            // A matching request may have consumed the predecessor after the first replay lookup.
            if (exception.reason() == DRAFT_UNAVAILABLE) {
                var completed = history.findReplay(user.id(), request.requestId(), prompt, request.replyTo());
                if (completed.isPresent()) {
                    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(completed.get());
                }
            }
            throw exception;
        }

        var suggestionService = suggestions.getIfAvailable();
        if (suggestionService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).cacheControl(CacheControl.noStore()).build();
        }

        var responses = suggestionService.suggestChat(user.id(), context);
        var entry = history.save(user.id(), revision, current, request.replyTo(), request.requestId(), responses);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(entry);
    }

    @DeleteMapping
    @Operation(summary = "Apaga todo o histórico de chat da conta, preservando transações registradas")
    public ResponseEntity<Void> clear(@AuthenticationPrincipal AuthenticatedUser user) {
        history.clear(user.id());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @DeleteMapping("/messages/{id}")
    @Operation(summary = "Apaga uma mensagem e sua resposta, preservando as demais mensagens e transações")
    public ResponseEntity<Void> deleteMessage(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
                                              @RequestParam(defaultValue = "false") boolean byRequestId) {
        history.deleteMessage(user.id(), id, byRequestId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @DeleteMapping("/drafts/{draftId}")
    @Operation(summary = "Descarta um rascunho pendente do chat")
    public ResponseEntity<Void> discard(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID draftId) {
        history.discard(user.id(), draftId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private static ZoneId zone(String name) {
        if (name == null) return DEFAULT_ZONE;
        if (!ZoneId.getAvailableZoneIds().contains(name)) {
            throw new TransactionChatException(INVALID_TIME_ZONE);
        }
        return ZoneId.of(name);
    }

    public record ChatRequest(@NotBlank @Size(max = 500) String description, UUID replyTo, UUID requestId,
                              @Size(max = 80) String timeZone) {
    }
}
