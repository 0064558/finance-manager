package com.rodrigs.finance_manager_api.transaction.service;

import com.rodrigs.finance_manager_api.transaction.dto.TransactionSuggestionResponseDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

@Service
public class TransactionChatHistory {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public TransactionChatHistory(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public List<Entry> findByUser(UUID userId) {
        // The existing creation key records confirmation in the same transaction as the financial write.
        return jdbc.query("""
                SELECT chat.id, chat.prompt, chat.response_body, creation.response_body IS NOT NULL AS saved
                FROM transaction_chat_history chat
                LEFT JOIN transaction_idempotency creation
                  ON creation.user_id = chat.user_id AND creation.idempotency_key = chat.id::text
                WHERE chat.user_id = ?
                ORDER BY chat.sequence_number
                """, (row, index) -> new Entry(row.getObject("id", UUID.class), row.getString("prompt"),
                json.readValue(row.getString("response_body"), TransactionSuggestionResponseDTO.class),
                row.getBoolean("saved")), userId);
    }

    public Entry save(UUID userId, String prompt, TransactionSuggestionResponseDTO response) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO transaction_chat_history (id, user_id, prompt, response_body)
                VALUES (?, ?, ?, ?)
                """, id, userId, prompt, json.writeValueAsString(response));
        return new Entry(id, prompt, response, false);
    }

    public record Entry(UUID id, String prompt, TransactionSuggestionResponseDTO response, boolean saved) {
    }
}
