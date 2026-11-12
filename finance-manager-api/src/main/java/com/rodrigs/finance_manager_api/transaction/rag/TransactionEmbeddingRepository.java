package com.rodrigs.finance_manager_api.transaction.rag;

import com.rodrigs.finance_manager_api.shared.enums.TransactionType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.function.Supplier;

@Repository
public class TransactionEmbeddingRepository {
    private static final int VECTOR_DIMENSIONS = 768;
    private static final String DESCRIPTION_HASH = "encode(sha256(convert_to(t.description, 'UTF8')), 'hex')";
    private static final String READY_FILTER = """
            WHERE t.user_id = ? AND e.embedding_version = ? AND e.embedding IS NOT NULL
              AND e.source_hash = %s
            """.formatted(DESCRIPTION_HASH);

    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactionManager;

    public TransactionEmbeddingRepository(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transactionManager = transactionManager;
    }

    @Transactional
    public int reconcile(UUID userId, String version, int backfillLimit, int batchLimit) {
        // The cutoff is set once per user, including when the initial cohort is empty.
        jdbc.update("""
                INSERT INTO transaction_embedding_pilots (user_id, from_created_at, from_transaction_id)
                SELECT u.id, COALESCE(c.created_at, statement_timestamp()),
                       COALESCE(c.id, '00000000-0000-0000-0000-000000000000'::uuid)
                FROM users u
                LEFT JOIN LATERAL (
                    SELECT recent.created_at, recent.id
                    FROM (
                        SELECT t.created_at, t.id
                        FROM transactions t
                        WHERE t.user_id = u.id AND t.description IS NOT NULL
                        ORDER BY t.created_at DESC, t.id DESC
                        LIMIT ?
                    ) recent
                    ORDER BY recent.created_at, recent.id
                    LIMIT 1
                ) c ON true
                WHERE u.id = ?
                ON CONFLICT (user_id) DO NOTHING
                """, backfillLimit, userId);
        jdbc.update("""
                WITH obsolete AS (
                    SELECT e.transaction_id
                    FROM transaction_embeddings e
                    JOIN transactions t ON t.id = e.transaction_id
                    WHERE t.user_id = ? AND t.description IS NULL
                    ORDER BY e.transaction_id
                    LIMIT ?
                    FOR UPDATE OF e SKIP LOCKED
                )
                DELETE FROM transaction_embeddings e USING obsolete
                WHERE e.transaction_id = obsolete.transaction_id
                """, userId, batchLimit);

        return jdbc.update("""
                WITH candidates AS (
                    SELECT t.id, %1$s AS source_hash
                    FROM transactions t
                    JOIN transaction_embedding_pilots p ON p.user_id = t.user_id
                    LEFT JOIN transaction_embeddings e ON e.transaction_id = t.id
                    WHERE t.user_id = ? AND t.description IS NOT NULL
                      AND (t.created_at, t.id) >= (p.from_created_at, p.from_transaction_id)
                      AND (e.transaction_id IS NULL OR e.embedding_version <> ? OR e.source_hash <> %1$s)
                    ORDER BY t.created_at DESC, t.id DESC
                    LIMIT ?
                    FOR SHARE OF t SKIP LOCKED
                )
                INSERT INTO transaction_embeddings (transaction_id, source_hash, embedding_version)
                SELECT id, source_hash, ? FROM candidates
                ON CONFLICT (transaction_id) DO UPDATE SET
                    source_hash = EXCLUDED.source_hash, embedding_version = EXCLUDED.embedding_version,
                    embedding = NULL, skipped = false, attempts = 0, available_at = statement_timestamp(),
                    lease_token = NULL, lease_until = NULL
                WHERE transaction_embeddings.source_hash <> EXCLUDED.source_hash
                   OR transaction_embeddings.embedding_version <> EXCLUDED.embedding_version
                """.formatted(DESCRIPTION_HASH), userId, version, batchLimit, version);
    }

    public Optional<Work> claim(UUID userId, String version, Duration lease) {
        UUID leaseToken = UUID.randomUUID();
        return jdbc.query("""
                WITH candidate AS (
                    SELECT e.transaction_id, t.user_id, t.description
                    FROM transaction_embeddings e
                    JOIN transactions t ON t.id = e.transaction_id
                    WHERE t.user_id = ? AND e.embedding_version = ? AND e.embedding IS NULL AND NOT e.skipped
                      AND e.available_at <= statement_timestamp()
                      AND (e.lease_until IS NULL OR e.lease_until <= statement_timestamp())
                      AND e.source_hash = %s
                    ORDER BY e.available_at, t.created_at DESC, e.transaction_id
                    LIMIT 1
                    FOR UPDATE OF e SKIP LOCKED
                )
                UPDATE transaction_embeddings e
                SET lease_token = ?, lease_until = clock_timestamp() + ? * interval '1 millisecond',
                    attempts = e.attempts + 1
                FROM candidate
                WHERE e.transaction_id = candidate.transaction_id
                RETURNING e.transaction_id, candidate.user_id, candidate.description, e.source_hash,
                          e.embedding_version, e.lease_token, e.attempts
                """.formatted(DESCRIPTION_HASH), (row, index) -> new Work(
                row.getObject("transaction_id", UUID.class), row.getObject("user_id", UUID.class),
                row.getString("description"), row.getString("source_hash"), row.getString("embedding_version"),
                row.getObject("lease_token", UUID.class), row.getInt("attempts")),
                userId, version, leaseToken, Math.max(1, lease.toMillis())).stream().findFirst();
    }

    public boolean complete(Work work, float[] vector) {
        return finish(work, vectorLiteral(vector), false, Duration.ZERO);
    }

    public boolean retry(Work work, Duration delay) {
        return finish(work, null, false, delay);
    }

    public boolean skip(Work work) {
        return finish(work, null, true, Duration.ZERO);
    }

    private boolean finish(Work work, String vector, boolean skipped, Duration delay) {
        // Read and lock the current source before updating its embedding, the same lock order used by deletion.
        return jdbc.update("""
                WITH source AS MATERIALIZED (
                    SELECT t.id
                    FROM transactions t
                    WHERE t.id = ? AND t.user_id = ? AND %s = ?
                    FOR SHARE
                )
                UPDATE transaction_embeddings e
                SET embedding = CAST(? AS vector), skipped = ?,
                    available_at = clock_timestamp() + ? * interval '1 millisecond',
                    lease_token = NULL, lease_until = NULL
                FROM source
                WHERE e.transaction_id = source.id AND e.source_hash = ? AND e.embedding_version = ?
                  AND e.lease_token = ? AND e.lease_until > clock_timestamp()
                """.formatted(DESCRIPTION_HASH), work.transactionId(), work.userId(), work.sourceHash(),
                vector, skipped, Math.max(0, delay.toMillis()), work.sourceHash(), work.version(), work.leaseToken()) == 1;
    }

    public boolean hasReady(UUID userId, String version, Duration timeout) {
        return Boolean.TRUE.equals(readWithTimeout(timeout, () -> jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM transaction_embeddings e
                    JOIN transactions t ON t.id = e.transaction_id
                """ + READY_FILTER + ")", Boolean.class, userId, version)));
    }

    public Coverage coverage(Set<UUID> userIds, String version) {
        if (userIds.isEmpty()) {
            return new Coverage(0, 0, 0);
        }
        var arguments = new ArrayList<Object>();
        arguments.add(version);
        arguments.addAll(userIds);
        String placeholders = String.join(",", Collections.nCopies(userIds.size(), "?"));
        return jdbc.queryForObject("""
                WITH cohort AS (
                    SELECT e.embedding, e.skipped,
                           e.embedding_version = ? AND e.source_hash = %s AS current
                    FROM transactions t
                    JOIN transaction_embedding_pilots p ON p.user_id = t.user_id
                    LEFT JOIN transaction_embeddings e ON e.transaction_id = t.id
                    WHERE t.user_id IN (%s) AND t.description IS NOT NULL
                      AND (t.created_at, t.id) >= (p.from_created_at, p.from_transaction_id)
                )
                SELECT count(*) AS total,
                       count(*) FILTER (WHERE current AND embedding IS NOT NULL) AS ready,
                       count(*) FILTER (WHERE current AND skipped) AS skipped
                FROM cohort
                """.formatted(DESCRIPTION_HASH, placeholders), (row, index) -> {
            long ready = row.getLong("ready");
            long skipped = row.getLong("skipped");
            return new Coverage(ready, row.getLong("total") - ready - skipped, skipped);
        }, arguments.toArray());
    }

    public List<Match> search(UUID userId, String version, float[] vector, double minSimilarity, int limit,
                              Duration timeout) {
        String literal = vectorLiteral(vector);
        return readWithTimeout(timeout, () -> jdbc.query("""
                SELECT t.description, t.category_id, t.type
                FROM transaction_embeddings e
                JOIN transactions t ON t.id = e.transaction_id
                CROSS JOIN (SELECT CAST(? AS vector) AS embedding) q
                """ + READY_FILTER + """
                  AND (e.embedding <=> q.embedding) <= ?
                ORDER BY e.embedding <=> q.embedding, t.id
                LIMIT ?
                """, (row, index) -> new Match(row.getString("description"), row.getObject("category_id", UUID.class),
                TransactionType.valueOf(row.getString("type"))), literal, userId, version, 1 - minSimilarity, limit));
    }

    private <T> T readWithTimeout(Duration timeout, Supplier<T> query) {
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Embedding lookup requires a positive timeout");
        }
        int milliseconds = (int) Math.clamp(timeout.toMillis(), 1, Integer.MAX_VALUE);
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setReadOnly(true);
        transaction.setTimeout((int) Math.ceil(milliseconds / 1000.0));
        return transaction.execute(status -> {
            // The PostgreSQL setting is precise to milliseconds and disappears when the transaction ends.
            jdbc.queryForObject("SELECT set_config('statement_timeout', ?, true)", String.class, milliseconds + "ms");
            return query.get();
        });
    }

    private static String vectorLiteral(float[] values) {
        if (values.length != VECTOR_DIMENSIONS) {
            throw new IllegalArgumentException("Embedding has an unexpected dimension");
        }
        var literal = new StringJoiner(",", "[", "]");
        boolean nonzero = false;
        for (float value : values) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Embedding values must be finite");
            }
            nonzero |= value != 0;
            literal.add(Float.toString(value));
        }
        if (!nonzero) {
            throw new IllegalArgumentException("Embedding must have a nonzero norm");
        }
        return literal.toString();
    }

    public record Work(UUID transactionId, UUID userId, String description, String sourceHash, String version,
                       UUID leaseToken, int attempts) {}

    public record Match(String description, UUID categoryId, TransactionType type) {}

    public record Coverage(long ready, long pending, long skipped) {
        public double ratio() {
            return ready + pending == 0 ? Double.NaN : (double) ready / (ready + pending);
        }
    }
}
