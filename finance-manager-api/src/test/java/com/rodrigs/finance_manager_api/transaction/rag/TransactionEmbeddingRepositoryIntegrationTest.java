package com.rodrigs.finance_manager_api.transaction.rag;

import com.rodrigs.finance_manager_api.shared.enums.TransactionType;
import com.rodrigs.finance_manager_api.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class TransactionEmbeddingRepositoryIntegrationTest extends PostgresIntegrationTest {
    private static final String VERSION = "embedding-v1";
    private static final Duration LEASE = Duration.ofMinutes(1);
    private static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(2);
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2020-01-01T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionEmbeddingRepository repository;

    @Test
    void measuresOnlyThePilotCohortAndCountsStaleMissingAndSkippedVectors() {
        Account owner = account();
        Account other = account();
        transaction(owner, "Fora do backfill", 1);
        transaction(owner, "12345", 2);
        UUID ready = transaction(owner, "Almoço", 3);
        transaction(other, "Privada", 1);
        repository.reconcile(owner.userId(), VERSION, 2, 1);

        assertThat(repository.coverage(Set.of(owner.userId()), VERSION))
                .isEqualTo(new TransactionEmbeddingRepository.Coverage(0, 2, 0));
        completeNext(owner, vector(1, 0));
        repository.reconcile(owner.userId(), VERSION, 2, 1);
        assertThat(repository.skip(repository.claim(owner.userId(), VERSION, LEASE).orElseThrow())).isTrue();
        assertThat(repository.coverage(Set.of(owner.userId()), VERSION))
                .isEqualTo(new TransactionEmbeddingRepository.Coverage(1, 0, 1));
        assertThat(repository.coverage(Set.of(owner.userId()), VERSION).ratio()).isEqualTo(1);

        transaction(owner, "Nova ainda sem índice", 4);
        assertThat(repository.coverage(Set.of(owner.userId(), other.userId()), VERSION))
                .isEqualTo(new TransactionEmbeddingRepository.Coverage(1, 1, 1));
        jdbc.update("UPDATE transactions SET description = 'Jantar' WHERE id = ?", ready);
        assertThat(repository.coverage(Set.of(owner.userId()), VERSION))
                .isEqualTo(new TransactionEmbeddingRepository.Coverage(0, 2, 1));
        assertThat(repository.coverage(Set.of(owner.userId()), "embedding-v2"))
                .isEqualTo(new TransactionEmbeddingRepository.Coverage(0, 3, 0));
        jdbc.update("DELETE FROM transactions WHERE id = ?", ready);
        assertThat(repository.coverage(Set.of(owner.userId()), VERSION))
                .isEqualTo(new TransactionEmbeddingRepository.Coverage(0, 1, 1));
        assertThat(repository.coverage(Set.of(), VERSION).ratio()).isNaN();
    }

    @Test
    void searchesOnlyOwnedAndCurrentVectorsAndUsesCurrentCategoryAndType() {
        Account owner = account();
        Account other = account();
        UUID closest = transaction(owner, "Corrida para o plantão", 1);
        transaction(owner, "Corrida para o turno", 2);
        transaction(owner, "Curso de Java", 3);
        transaction(other, "Corrida privada para o plantão", 1);

        repository.reconcile(owner.userId(), VERSION, 100, 100);
        repository.reconcile(other.userId(), VERSION, 100, 100);
        completeNext(owner, vector(0, 1));
        completeNext(owner, vector(0.8f, 0.2f));
        completeNext(owner, vector(1, 0));
        completeNext(other, vector(1, 0));

        assertThat(repository.hasReady(owner.userId(), VERSION, LOOKUP_TIMEOUT)).isTrue();
        assertThat(repository.hasReady(owner.userId(), "embedding-v2", LOOKUP_TIMEOUT)).isFalse();
        assertThat(search(owner, 3)).extracting(TransactionEmbeddingRepository.Match::description)
                .containsExactly("Corrida para o plantão", "Corrida para o turno");
        assertThat(search(owner, 1)).extracting(TransactionEmbeddingRepository.Match::description)
                .containsExactly("Corrida para o plantão");

        UUID income = category(owner.userId(), "Reembolsos", TransactionType.INCOME);
        jdbc.update("UPDATE transactions SET category_id = ?, type = 'INCOME' WHERE id = ?", income, closest);
        assertThat(search(owner, 1)).containsExactly(
                new TransactionEmbeddingRepository.Match("Corrida para o plantão", income, TransactionType.INCOME));

        jdbc.update("UPDATE transactions SET description = 'Reembolso do plantão' WHERE id = ?", closest);
        assertThat(search(owner, 3)).extracting(TransactionEmbeddingRepository.Match::description)
                .containsExactly("Corrida para o turno");
    }

    @Test
    void retainsTheInitialBackfillCutoffAcrossBatchesAndIncludesNewTransactions() {
        Account owner = account();
        UUID excluded = transaction(owner, "Antiga fora do lote inicial", 1);
        UUID oldestEligible = transaction(owner, "Antiga incluída no lote inicial", 2);
        UUID newestEligible = transaction(owner, "Mais recente no lote inicial", 3);

        assertThat(repository.reconcile(owner.userId(), VERSION, 2, 1)).isEqualTo(1);
        UUID newTransaction = transaction(owner, "Nova transação", 4);
        UUID anotherNewTransaction = transaction(owner, "Outra transação nova", 5);

        assertThat(repository.reconcile(owner.userId(), VERSION, 2, 1)).isEqualTo(1);
        assertThat(repository.reconcile(owner.userId(), VERSION, 2, 10)).isEqualTo(2);
        assertThat(repository.reconcile(owner.userId(), VERSION, 2, 10)).isZero();
        assertThat(jdbc.queryForList("SELECT transaction_id FROM transaction_embeddings", UUID.class))
                .containsExactlyInAnyOrder(oldestEligible, newestEligible, newTransaction, anotherNewTransaction)
                .doesNotContain(excluded);
    }

    @Test
    void reservesTheSameTransactionOnlyOnceUnderConcurrentClaims() throws Exception {
        Account owner = account();
        UUID transaction = transaction(owner, "Almoço", 1);
        repository.reconcile(owner.userId(), VERSION, 100, 100);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var threads = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var first = threads.submit(() -> {
                start.await();
                return repository.claim(owner.userId(), VERSION, LEASE);
            });
            var second = threads.submit(() -> {
                start.await();
                return repository.claim(owner.userId(), VERSION, LEASE);
            });
            start.countDown();
            var claims = List.of(first.get(5, java.util.concurrent.TimeUnit.SECONDS),
                            second.get(5, java.util.concurrent.TimeUnit.SECONDS)).stream()
                    .flatMap(java.util.Optional::stream).toList();
            assertThat(claims).singleElement().satisfies(work -> assertThat(work.transactionId()).isEqualTo(transaction));
            assertThat(repository.complete(claims.getFirst(), vector(1, 0))).isTrue();
            assertThat(repository.complete(claims.getFirst(), vector(1, 0))).isFalse();
        }
    }

    @Test
    void expiresLeasesAndWaitsForRetryBeforeClaimingAgain() {
        Account owner = account();
        UUID id = transaction(owner, "Almoço", 1);
        repository.reconcile(owner.userId(), VERSION, 100, 100);

        var original = repository.claim(owner.userId(), VERSION, LEASE).orElseThrow();
        assertThat(original.attempts()).isEqualTo(1);
        assertThat(repository.claim(owner.userId(), VERSION, LEASE)).isEmpty();
        jdbc.update("UPDATE transaction_embeddings SET lease_until = statement_timestamp() - interval '1 second' WHERE transaction_id = ?", id);
        assertThat(repository.complete(original, vector(1, 0))).isFalse();

        var reclaimed = repository.claim(owner.userId(), VERSION, LEASE).orElseThrow();
        assertThat(reclaimed.attempts()).isEqualTo(2);
        assertThat(reclaimed.leaseToken()).isNotEqualTo(original.leaseToken());
        assertThat(repository.retry(reclaimed, Duration.ofDays(1))).isTrue();
        assertThat(repository.claim(owner.userId(), VERSION, LEASE)).isEmpty();

        jdbc.update("UPDATE transaction_embeddings SET available_at = statement_timestamp() - interval '1 second' WHERE transaction_id = ?", id);
        var retried = repository.claim(owner.userId(), VERSION, LEASE).orElseThrow();
        assertThat(retried.attempts()).isEqualTo(3);
        assertThat(repository.complete(original, vector(1, 0))).isFalse();
        assertThat(repository.complete(retried, vector(1, 0))).isTrue();
        assertThat(repository.hasReady(owner.userId(), VERSION, LOOKUP_TIMEOUT)).isTrue();
    }

    @Test
    void invalidatesWorkOnEditAndCascadesWhenTheTransactionIsDeleted() {
        Account owner = account();
        UUID id = transaction(owner, "Descrição original", 1);
        repository.reconcile(owner.userId(), VERSION, 100, 100);
        var original = repository.claim(owner.userId(), VERSION, LEASE).orElseThrow();

        jdbc.update("UPDATE transactions SET description = '12345' WHERE id = ?", id);
        assertThat(repository.complete(original, vector(1, 0))).isFalse();
        assertThat(repository.reconcile(owner.userId(), VERSION, 100, 100)).isEqualTo(1);
        var skipped = repository.claim(owner.userId(), VERSION, LEASE).orElseThrow();
        assertThat(skipped.description()).isEqualTo("12345");
        assertThat(repository.skip(skipped)).isTrue();
        assertThat(repository.reconcile(owner.userId(), VERSION, 100, 100)).isZero();
        assertThat(repository.claim(owner.userId(), VERSION, LEASE)).isEmpty();

        jdbc.update("UPDATE transactions SET description = NULL WHERE id = ?", id);
        repository.reconcile(owner.userId(), VERSION, 100, 100);
        assertThat(jdbc.queryForList("SELECT transaction_id FROM transaction_embeddings", UUID.class)).isEmpty();

        jdbc.update("UPDATE transactions SET description = 'Mercado' WHERE id = ?", id);
        repository.reconcile(owner.userId(), VERSION, 100, 100);
        var latest = repository.claim(owner.userId(), VERSION, LEASE).orElseThrow();
        jdbc.update("DELETE FROM transactions WHERE id = ?", id);
        assertThat(repository.complete(latest, vector(1, 0))).isFalse();
        assertThat(jdbc.queryForList("SELECT transaction_id FROM transaction_embeddings", UUID.class)).isEmpty();
    }

    private Account account() {
        UUID userId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, name, email, password_hash) VALUES (?, 'Teste', ?, 'test')",
                userId, userId + "@example.invalid");
        jdbc.update("""
                INSERT INTO financial_accounts (id, user_id, name, type, initial_balance)
                VALUES (?, ?, 'Carteira', 'CASH', 0)
                """, accountId, userId);
        return new Account(userId, accountId, category(userId, "Despesas", TransactionType.EXPENSE));
    }

    private UUID category(UUID userId, String name, TransactionType type) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO categories (id, user_id, name, transaction_type) VALUES (?, ?, ?, ?)",
                id, userId, name, type.name());
        return id;
    }

    private UUID transaction(Account account, String description, int day) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO transactions (id, user_id, account_id, category_id, type, amount,
                                          occurred_on, description, created_at)
                VALUES (?, ?, ?, ?, 'EXPENSE', 10, DATE '2020-01-01', ?, ?)
                """, id, account.userId(), account.accountId(), account.categoryId(), description, CREATED_AT.plusDays(day));
        return id;
    }

    private void completeNext(Account account, float[] values) {
        assertThat(repository.complete(repository.claim(account.userId(), VERSION, LEASE).orElseThrow(), values)).isTrue();
    }

    private List<TransactionEmbeddingRepository.Match> search(Account account, int limit) {
        return repository.search(account.userId(), VERSION, vector(1, 0), 0.55, limit, LOOKUP_TIMEOUT);
    }

    private static float[] vector(float first, float second) {
        float[] vector = new float[768];
        vector[0] = first;
        vector[1] = second;
        return vector;
    }

    private record Account(UUID userId, UUID accountId, UUID categoryId) {}
}
