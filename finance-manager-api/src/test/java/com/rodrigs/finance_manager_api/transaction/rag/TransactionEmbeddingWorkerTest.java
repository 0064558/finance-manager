package com.rodrigs.finance_manager_api.transaction.rag;

import com.rodrigs.finance_manager_api.transaction.rag.TransactionEmbeddingRepository.Work;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TransactionEmbeddingWorkerTest {
    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Duration LEASE = Duration.ofSeconds(30);
    private final TransactionRagProperties properties = properties(true);
    private final TransactionEmbeddingRepository repository = mock(TransactionEmbeddingRepository.class);
    private final TransactionEmbeddingClient client = mock(TransactionEmbeddingClient.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final TransactionEmbeddingWorker worker = new TransactionEmbeddingWorker(properties, repository, client, meters);

    @Test
    void indexesMinimizedTextAndSkipsUninformativeDescriptions() {
        Work eligible = work("Uber no plantão R$ 22,00", 1);
        Work skipped = work("CPF: 123.456.789-09", 1);
        float[] vector = {0.1f};
        when(repository.claim(USER, properties.version(), LEASE)).thenReturn(Optional.of(eligible), Optional.of(skipped));
        when(client.embedDocument("Uber no plantão")).thenReturn(vector);
        when(repository.complete(eligible, vector)).thenReturn(true);
        when(repository.skip(skipped)).thenReturn(true);

        worker.run();

        verify(repository).reconcile(USER, properties.version(), 100, 2);
        assertThat(indexed("indexed")).isEqualTo(1);
        assertThat(indexed("skipped")).isEqualTo(1);
    }

    @Test
    void retriesTransientFailuresAndContinuesIndexingTheBatch() {
        Work failed = work("Uber para o mercado", 2);
        Work eligible = work("Ônibus para o centro", 1);
        float[] vector = {0.1f};
        when(repository.claim(USER, properties.version(), LEASE)).thenReturn(Optional.of(failed), Optional.of(eligible));
        when(client.embedDocument("Uber para o mercado")).thenThrow(new TransactionEmbeddingException(503));
        when(client.embedDocument("Ônibus para o centro")).thenReturn(vector);
        when(repository.retry(failed, Duration.ofMinutes(1))).thenReturn(true);
        when(repository.complete(eligible, vector)).thenReturn(true);

        worker.run();

        verify(repository).retry(failed, Duration.ofMinutes(1));
        assertThat(indexed("retry")).isEqualTo(1);
        assertThat(indexed("indexed")).isEqualTo(1);
    }

    @Test
    void pausesTheBatchAndSubsequentTicksWhenCredentialsAreRejected() {
        Work failed = work("Uber para o mercado", 1);
        when(repository.claim(USER, properties.version(), LEASE)).thenReturn(Optional.of(failed));
        when(client.embedDocument("Uber para o mercado")).thenThrow(new TransactionEmbeddingException(403));
        when(repository.retry(failed, Duration.ofMinutes(5))).thenReturn(true);

        worker.run();
        worker.run();

        verify(client).embedDocument("Uber para o mercado");
        verify(repository).reconcile(USER, properties.version(), 100, 2);
        verify(repository).retry(failed, Duration.ofMinutes(5));
        assertThat(indexed("retry")).isEqualTo(1);
    }

    @Test
    void disabledConfigurationLeavesProviderAndRepositoryIdle() {
        new TransactionEmbeddingWorker(properties(false), repository, client, meters).run();

        verifyNoInteractions(repository, client);
    }

    private double indexed(String outcome) {
        return meters.get("finance.manager.chat.rag.indexing").tag("outcome", outcome).counter().count();
    }

    private TransactionRagProperties properties(boolean enabled) {
        return new TransactionRagProperties(enabled, Set.of(USER), "gemini-embedding-001", Duration.ofSeconds(2), 0.55, 3, 100, 2);
    }

    private Work work(String text, int attempts) {
        return new Work(UUID.randomUUID(), USER, text, "hash", properties.version(), UUID.randomUUID(), attempts);
    }
}
