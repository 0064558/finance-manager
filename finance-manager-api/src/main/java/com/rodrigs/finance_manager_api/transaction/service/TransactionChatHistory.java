package com.rodrigs.finance_manager_api.transaction.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rodrigs.finance_manager_api.shared.exception.TransactionChatException;
import com.rodrigs.finance_manager_api.transaction.dto.TransactionSuggestionResponseDTO;
import com.rodrigs.finance_manager_api.transaction.dto.TransactionSuggestionResponseDTO.Status;
import com.rodrigs.finance_manager_api.transaction.service.TransactionSuggestionService.ChatMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.rodrigs.finance_manager_api.shared.exception.TransactionChatException.Reason.CONTEXT_LIMIT;
import static com.rodrigs.finance_manager_api.shared.exception.TransactionChatException.Reason.DRAFT_UNAVAILABLE;
import static com.rodrigs.finance_manager_api.shared.exception.TransactionChatException.Reason.ENTRY_NOT_FOUND;
import static com.rodrigs.finance_manager_api.shared.exception.TransactionChatException.Reason.HISTORY_CHANGED;
import static com.rodrigs.finance_manager_api.shared.exception.TransactionChatException.Reason.REQUEST_CONFLICT;

@Service
public class TransactionChatHistory {
    private static final int MAX_MESSAGES_PER_THREAD = 12;

    private static final String ENTRIES = """
            SELECT chat.id, chat.prompt, chat.response_body, chat.reply_to, chat.request_id, chat.deleted,
                   EXISTS (SELECT 1 FROM transaction_chat_history reply WHERE reply.reply_to = chat.id) AS superseded,
                   COALESCE((
                       SELECT jsonb_agg(jsonb_build_object(
                           'id', draft.id, 'response', draft.response_body::jsonb, 'discarded', draft.discarded,
                           'saved', creation.response_body IS NOT NULL
                       ) ORDER BY draft.position)
                       FROM transaction_chat_drafts draft
                       LEFT JOIN transaction_idempotency creation
                         ON creation.user_id = chat.user_id AND creation.idempotency_key = draft.id::text
                       WHERE draft.entry_id = chat.id AND draft.response_body <> 'null'
                   ), '[]'::jsonb)::text AS drafts
            FROM transaction_chat_history chat
            WHERE chat.user_id = ?
            """;
    private static final String ERASE_ENTRY = """
            UPDATE transaction_chat_history
            SET deleted = true, prompt = '', response_body = NULL, reference_date = NULL
            WHERE user_id = ? AND NOT deleted %s
            RETURNING id
            """;
    private static final String ERASE_RESPONSE = """
            UPDATE transaction_chat_history chat
            SET response_body = NULL
            WHERE user_id = ? AND id = ? AND NOT deleted
              AND (response_body IS NOT NULL OR EXISTS (
                  SELECT 1 FROM transaction_chat_drafts draft
                  WHERE draft.entry_id = chat.id AND draft.response_body <> 'null'
              ))
            RETURNING id
            """;
    // Keep draft keys so an old confirmation cannot turn into a manual creation.
    private static final String ERASE_DRAFTS = """
            ), erased_drafts AS (
                UPDATE transaction_chat_drafts draft
                SET response_body = 'null', discarded = true
                FROM erased WHERE draft.entry_id = erased.id
            )
            SELECT EXISTS (SELECT 1 FROM erased)
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public TransactionChatHistory(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public List<Entry> findByUser(UUID userId) {
        return jdbc.query(ENTRIES + "AND NOT chat.deleted ORDER BY chat.sequence_number", this::readEntry, userId);
    }

    public long revision(UUID userId) {
        return jdbc.queryForObject("SELECT chat_history_revision FROM users WHERE id = ?", Long.class, userId);
    }

    @Transactional
    public void clear(UUID userId) {
        // Advance even if the history is empty to invalidate requests still at the model.
        advanceRevision(userId);
        erase(ERASE_ENTRY.formatted(""), userId);
    }

    @Transactional
    public void deleteMessage(UUID userId, UUID id, boolean byRequestId) {
        lockRevision(userId);
        var filter = byRequestId ? "AND request_id = ?" : "AND id = ?";
        boolean erased = erase(ERASE_ENTRY.formatted(filter), userId, id);
        if (byRequestId && !erased) {
            // A timed-out send can still be at the model. Reserve its request ID before it saves.
            erased = jdbc.update("""
                    INSERT INTO transaction_chat_history (id, user_id, prompt, request_id, deleted)
                    VALUES (?, ?, '', ?, true) ON CONFLICT (user_id, request_id) DO NOTHING
                    """, UUID.randomUUID(), userId, id) == 1;
        }
        if (erased) advanceRevision(userId);
    }

    @Transactional
    public void deleteResponse(UUID userId, UUID id) {
        lockRevision(userId);
        if (erase(ERASE_RESPONSE, userId, id)) advanceRevision(userId);
    }

    private boolean erase(String update, Object... arguments) {
        return Boolean.TRUE.equals(jdbc.queryForObject("WITH erased AS (" + update + ERASE_DRAFTS,
                Boolean.class, arguments));
    }

    private long lockRevision(UUID userId) {
        return jdbc.queryForObject("SELECT chat_history_revision FROM users WHERE id = ? FOR NO KEY UPDATE",
                Long.class, userId);
    }

    private void advanceRevision(UUID userId) {
        jdbc.update("UPDATE users SET chat_history_revision = chat_history_revision + 1 WHERE id = ?", userId);
    }

    public Optional<Entry> findReplay(UUID userId, UUID requestId, String prompt, UUID replyTo) {
        if (requestId == null) return Optional.empty();
        return jdbc.query(ENTRIES + "AND chat.request_id = ?", (row, index) -> {
                    if (row.getBoolean("deleted")) throw new TransactionChatException(HISTORY_CHANGED);
                    return readEntry(row, index);
                }, userId, requestId)
                .stream()
                .findFirst()
                .map(existing -> {
                    if (!existing.prompt().equals(prompt) || !Objects.equals(existing.replyTo(), replyTo)) {
                        throw new TransactionChatException(REQUEST_CONFLICT);
                    }
                    return existing;
                });
    }

    public List<ChatMessage> context(UUID userId, UUID replyTo, ChatMessage current) {
        if (replyTo == null) return List.of(current);
        var parent = jdbc.query(ENTRIES + "AND chat.id = ? AND NOT chat.deleted", this::readEntry, userId, replyTo).stream()
                .findFirst()
                .orElseThrow(() -> new TransactionChatException(ENTRY_NOT_FOUND));
        if (parent.superseded() || (parent.response() == null && parent.drafts().isEmpty()) || parent.drafts().size() > 1
                || parent.drafts().stream().anyMatch(draft -> draft.saved() || draft.discarded())) {
            throw new TransactionChatException(DRAFT_UNAVAILABLE);
        }

        var ancestors = jdbc.query("""
                WITH RECURSIVE ancestry (prompt, reference_date, reply_to, deleted, depth) AS (
                    SELECT prompt, reference_date, reply_to, deleted, 1
                    FROM transaction_chat_history WHERE user_id = ? AND id = ?
                    UNION ALL
                    SELECT parent.prompt, parent.reference_date, parent.reply_to, parent.deleted, ancestry.depth + 1
                    FROM transaction_chat_history parent JOIN ancestry ON parent.id = ancestry.reply_to
                    WHERE parent.user_id = ? AND ancestry.depth < ?
                )
                SELECT prompt, reference_date, deleted FROM ancestry ORDER BY depth DESC
                """, (row, index) -> {
                    if (row.getBoolean("deleted")) throw new TransactionChatException(DRAFT_UNAVAILABLE);
                    return new ChatMessage(row.getString("prompt"), row.getObject("reference_date", LocalDate.class));
                },
                userId, replyTo, userId, MAX_MESSAGES_PER_THREAD);
        if (ancestors.isEmpty()) throw new TransactionChatException(ENTRY_NOT_FOUND);
        if (ancestors.size() == MAX_MESSAGES_PER_THREAD) throw new TransactionChatException(CONTEXT_LIMIT);
        var messages = new ArrayList<>(ancestors);
        messages.add(current);
        return List.copyOf(messages);
    }

    @Transactional
    public Entry save(UUID userId, long revision, ChatMessage current, UUID replyTo, UUID requestId,
                      List<TransactionSuggestionResponseDTO> responses) {
        if (lockRevision(userId) != revision) throw new TransactionChatException(HISTORY_CHANGED);
        if (replyTo != null) lockEntry(userId, replyTo);
        var replay = findReplay(userId, requestId, current.text(), replyTo);
        if (replay.isPresent()) return replay.get();
        if (replyTo != null) requireReplyable(userId, replyTo);

        UUID id = UUID.randomUUID();
        var clarification = responses.getFirst().status() == Status.ESCLARECIMENTO ? responses.getFirst() : null;
        int inserted = jdbc.update("""
                INSERT INTO transaction_chat_history (id, user_id, prompt, response_body, reference_date, reply_to, request_id)
                VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (user_id, request_id) DO NOTHING
                """, id, userId, current.text(), clarification == null ? null : json.writeValueAsString(clarification),
                current.referenceDate(), replyTo, requestId);
        if (inserted == 0) return findReplay(userId, requestId, current.text(), replyTo).orElseThrow();

        if (clarification != null) {
            return new Entry(id, current.text(), clarification, false, List.of(), replyTo, false, requestId);
        }

        var drafts = new ArrayList<Draft>();
        for (int position = 0; position < responses.size(); position++) {
            UUID draftId = responses.size() == 1 ? id : UUID.randomUUID();
            var response = responses.get(position);
            jdbc.update("""
                    INSERT INTO transaction_chat_drafts (id, entry_id, position, response_body)
                    VALUES (?, ?, ?, ?)
                    """, draftId, id, position, json.writeValueAsString(response));
            drafts.add(new Draft(draftId, response, false, false));
        }
        return new Entry(id, current.text(), drafts.size() == 1 ? drafts.getFirst().response() : null, false,
                List.copyOf(drafts), replyTo, false, requestId);
    }

    @Transactional
    public void discard(UUID userId, UUID draftId) {
        var draft = lockDraft(userId, draftId).orElseThrow(() -> new TransactionChatException(ENTRY_NOT_FOUND));
        if (draft.discarded()) return;
        if (hasSuccessor(draft.entryId()) || wasConfirmed(userId, draftId)) {
            throw new TransactionChatException(DRAFT_UNAVAILABLE);
        }
        jdbc.update("UPDATE transaction_chat_drafts SET discarded = true WHERE id = ?", draftId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<String> lockForConfirmation(UUID userId, String key) {
        if (key.length() != 36) return Optional.empty();
        UUID id;
        try {
            id = UUID.fromString(key);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        if (!id.toString().equals(key)) return Optional.empty();

        return lockDraft(userId, id).map(draft -> {
            if ((draft.discarded() || hasSuccessor(draft.entryId())) && !wasConfirmed(userId, id)) {
                throw new TransactionChatException(DRAFT_UNAVAILABLE);
            }
            return draft.suggestedCategoryId();
        });
    }

    private void lockEntry(UUID userId, UUID id) {
        if (jdbc.queryForList("SELECT id FROM transaction_chat_history WHERE user_id = ? AND id = ? AND NOT deleted FOR UPDATE",
                UUID.class, userId, id).isEmpty()) {
            throw new TransactionChatException(ENTRY_NOT_FOUND);
        }
    }

    private Optional<LockedDraft> lockDraft(UUID userId, UUID id) {
        return jdbc.query("""
                SELECT draft.entry_id, draft.discarded, draft.response_body::jsonb ->> 'categoriaId' AS suggested_category_id
                FROM transaction_chat_drafts draft JOIN transaction_chat_history chat ON chat.id = draft.entry_id
                WHERE draft.id = ? AND chat.user_id = ? FOR UPDATE OF draft
                """, (row, index) -> new LockedDraft(row.getObject("entry_id", UUID.class), row.getBoolean("discarded"),
                row.getString("suggested_category_id")),
                id, userId).stream().findFirst();
    }

    private void requireReplyable(UUID userId, UUID entryId) {
        var draft = lockDraft(userId, entryId);
        // A single draft has the entry ID. Entries with drafts under other IDs are batches.
        boolean batch = draft.isEmpty() && Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM transaction_chat_drafts WHERE entry_id = ?)
                """, Boolean.class, entryId));
        if (batch || hasSuccessor(entryId)
                || draft.filter(value -> value.discarded() || wasConfirmed(userId, entryId)).isPresent()) {
            throw new TransactionChatException(DRAFT_UNAVAILABLE);
        }
    }

    private boolean hasSuccessor(UUID entryId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM transaction_chat_history WHERE reply_to = ?)
                """, Boolean.class, entryId));
    }

    private boolean wasConfirmed(UUID userId, UUID draftId) {
        // Check in a separate statement after locking the draft to see a competing committed creation.
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM transaction_idempotency
                    WHERE user_id = ? AND idempotency_key = ? AND response_body IS NOT NULL
                )
                """, Boolean.class, userId, draftId.toString()));
    }

    private Entry readEntry(ResultSet row, int index) throws SQLException {
        var drafts = List.of(json.readValue(row.getString("drafts"), Draft[].class));
        String clarification = row.getString("response_body");
        Draft single = drafts.size() == 1 ? drafts.getFirst() : null;
        var response = single != null ? single.response() : clarification == null ? null
                : json.readValue(clarification, TransactionSuggestionResponseDTO.class);
        return new Entry(row.getObject("id", UUID.class), row.getString("prompt"), response,
                single != null && single.saved(), drafts, row.getObject("reply_to", UUID.class),
                row.getBoolean("superseded"), row.getObject("request_id", UUID.class));
    }

    private record LockedDraft(UUID entryId, boolean discarded, String suggestedCategoryId) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Entry(UUID id, String prompt, TransactionSuggestionResponseDTO response, boolean saved,
                        List<Draft> drafts, UUID replyTo, boolean superseded, UUID requestId) {
    }

    public record Draft(UUID id, TransactionSuggestionResponseDTO response, boolean saved, boolean discarded) {
    }
}
