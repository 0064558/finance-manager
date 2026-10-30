package com.rodrigs.finance_manager_api.transaction.service;

import com.rodrigs.finance_manager_api.shared.exception.IdempotencyConflictException;
import com.rodrigs.finance_manager_api.shared.exception.InvalidIdempotencyKeyException;
import com.rodrigs.finance_manager_api.transaction.dto.CreateTransactionRequestDTO;
import com.rodrigs.finance_manager_api.transaction.dto.TransactionResponseDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class IdempotentTransactionService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransactionService transactions;

    public IdempotentTransactionService(JdbcTemplate jdbc, ObjectMapper json, TransactionService transactions) {
        this.jdbc = jdbc;
        this.json = json;
        this.transactions = transactions;
    }

    @Transactional
    public TransactionResponseDTO create(UUID userId, CreateTransactionRequestDTO request, String key) {
        // Older API clients may omit the header; the app sends one per operation.
        if (key == null) return transactions.createTransaction(userId, request);
        if (key.isBlank() || key.length() > 200) throw new InvalidIdempotencyKeyException();

        String hash = fingerprint(request);
        // The unique key waits for any competing uncommitted insert. Both the
        // claim and financial write commit together, or both roll back.
        jdbc.update("""
                INSERT INTO transaction_idempotency (user_id, idempotency_key, request_hash)
                VALUES (?, ?, ?) ON CONFLICT (user_id, idempotency_key) DO NOTHING
                """, userId, key, hash);
        var saved = jdbc.queryForMap("""
                SELECT request_hash, response_body FROM transaction_idempotency
                WHERE user_id = ? AND idempotency_key = ? FOR UPDATE
                """, userId, key);
        if (!hash.equals(saved.get("request_hash"))) throw new IdempotencyConflictException();
        if (saved.get("response_body") instanceof String response) {
            return json.readerFor(TransactionResponseDTO.class)
                    .without(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                    .readValue(response);
        }

        var response = transactions.createTransaction(userId, request);
        jdbc.update("""
                UPDATE transaction_idempotency SET response_body = ?
                WHERE user_id = ? AND idempotency_key = ?
                """, json.writeValueAsString(response), userId, key);
        return response;
    }

    private String fingerprint(CreateTransactionRequestDTO request) {
        var normalized = new CreateTransactionRequestDTO(request.accountId(), request.categoryId(), request.type(),
                request.amount().stripTrailingZeros(), request.occurredOn(),
                request.description() == null ? null : request.description().trim());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(json.writeValueAsString(normalized).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
