package com.rodrigs.finance_manager_api.transaction.rag;

import com.rodrigs.finance_manager_api.transaction.rag.TransactionEmbeddingRepository.Coverage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TransactionEmbeddingMetricsTest {
    private final TransactionEmbeddingRepository repository = mock(TransactionEmbeddingRepository.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final TransactionRagProperties properties = properties(true);

    @Test
    void publishesAggregateCoverageAndPreservesTheLastSnapshotOnDatabaseFailure() {
        var metrics = new TransactionEmbeddingMetrics(properties, repository, meters);
        assertThat(gauge("finance.manager.chat.rag.coverage")).isNaN();
        when(repository.coverage(properties.pilotUserIds(), properties.version()))
                .thenReturn(new Coverage(3, 1, 2)).thenThrow(new IllegalStateException("Unavailable"));

        metrics.refresh();

        assertThat(gauge("finance.manager.chat.rag.coverage")).isEqualTo(0.75);
        assertThat(vectors("ready")).isEqualTo(3);
        assertThat(vectors("pending")).isEqualTo(1);
        assertThat(vectors("skipped")).isEqualTo(2);
        double updatedAt = gauge("finance.manager.chat.rag.coverage.updated");
        assertThat(updatedAt).isPositive();
        metrics.refresh();
        assertThat(gauge("finance.manager.chat.rag.coverage")).isEqualTo(0.75);
        assertThat(gauge("finance.manager.chat.rag.coverage.updated")).isEqualTo(updatedAt);
        assertThat(meters.get("finance.manager.chat.rag.coverage.failures").counter().count()).isOne();
        assertThat(meters.getMeters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags()).noneMatch(tag -> tag.getKey().equals("userId")));
    }

    @Test
    void leavesAnEmptyCohortUndefinedAndDoesNotQueryWhenDisabled() {
        var metrics = new TransactionEmbeddingMetrics(properties(false), repository, meters);
        metrics.refresh();
        verifyNoInteractions(repository);
        assertThat(new Coverage(0, 0, 2).ratio()).isNaN();
    }

    private double gauge(String name) {
        return meters.get(name).gauge().value();
    }

    private double vectors(String state) {
        return meters.get("finance.manager.chat.rag.vectors").tag("state", state).gauge().value();
    }

    private TransactionRagProperties properties(boolean enabled) {
        return new TransactionRagProperties(enabled, Set.of(UUID.randomUUID()), "gemini-embedding-001",
                Duration.ofSeconds(2), 0.55, 3, 100, 20);
    }
}
