package com.rodrigs.finance_manager_api.transaction.rag;

import com.rodrigs.finance_manager_api.shared.enums.TransactionType;
import com.rodrigs.finance_manager_api.transaction.rag.TransactionEmbeddingRepository.Match;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TransactionRagRetrieverTest {
    private static final Duration SEARCH_BUDGET = Duration.ofSeconds(2);
    private static final String SEARCH_METRIC = "finance.manager.chat.rag.search";
    private static final String EXAMPLES_METRIC = "finance.manager.chat.rag.examples";
    private static final String QUERY = "Paguei R$ 20,00 no mercado";

    private final UUID userId = UUID.randomUUID();
    private final TransactionEmbeddingRepository repository = mock(TransactionEmbeddingRepository.class);
    private final TransactionEmbeddingClient client = mock(TransactionEmbeddingClient.class);
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();

    @Test
    void skipsDisabledAndNonPilotUsersWithoutCallingDependencies() {
        try (var disabled = retriever(false, SEARCH_BUDGET); var enabled = retriever(true, SEARCH_BUDGET)) {
            assertThat(disabled.findExamples(userId, QUERY)).isEmpty();
            assertThat(enabled.findExamples(UUID.randomUUID(), QUERY)).isEmpty();
        }
        verifyNoInteractions(repository, client);
        assertThat(metrics.find(SEARCH_METRIC).timers()).isEmpty();
    }

    @Test
    void skipsEmbeddingsWhenTheUserHasNoReadyHistory() {
        try (var enabled = retriever(true, SEARCH_BUDGET)) {
            assertThat(enabled.findExamples(userId, QUERY)).isEmpty();
        }
        verify(repository).hasReady(eq(userId), eq(properties(true, SEARCH_BUDGET).version()), any(Duration.class));
        verifyNoInteractions(client);
        assertThat(outcomeCount("empty_history")).isOne();
    }

    @Test
    void minimizesQueryAndExamplesAndPassesTheRemainingBudgetToEachDependency() {
        var version = properties(true, SEARCH_BUDGET).version();
        var vector = new float[768];
        vector[0] = 1;
        UUID categoryId = UUID.randomUUID();
        when(repository.hasReady(eq(userId), eq(version), any(Duration.class))).thenReturn(true);
        when(client.embedQuery(eq("Paguei no mercado"), any(Duration.class))).thenReturn(vector);
        when(repository.search(eq(userId), eq(version), same(vector), eq(0.55), eq(3), any(Duration.class)))
                .thenReturn(List.of(new Match("Compra no mercado por R$ 18,00", categoryId, TransactionType.EXPENSE)));

        try (var enabled = retriever(true, SEARCH_BUDGET)) {
            assertThat(enabled.findExamples(userId, QUERY)).containsExactly(
                    new Match("Compra no mercado por", categoryId, TransactionType.EXPENSE));
        }

        var readyBudget = ArgumentCaptor.forClass(Duration.class);
        var embeddingBudget = ArgumentCaptor.forClass(Duration.class);
        var searchBudget = ArgumentCaptor.forClass(Duration.class);
        verify(repository).hasReady(eq(userId), eq(version), readyBudget.capture());
        verify(client).embedQuery(eq("Paguei no mercado"), embeddingBudget.capture());
        verify(repository).search(eq(userId), eq(version), same(vector), eq(0.55), eq(3), searchBudget.capture());
        assertThat(readyBudget.getValue()).isLessThanOrEqualTo(SEARCH_BUDGET);
        assertThat(embeddingBudget.getValue()).isLessThanOrEqualTo(readyBudget.getValue());
        assertThat(searchBudget.getValue()).isLessThanOrEqualTo(embeddingBudget.getValue());
        assertThat(outcomeCount("hit")).isOne();
        assertThat(metrics.get(EXAMPLES_METRIC).summary().totalAmount()).isEqualTo(1);
    }

    @Test
    void usesEmptyHistoryOnProviderError() {
        when(repository.hasReady(eq(userId), eq(properties(true, SEARCH_BUDGET).version()), any(Duration.class))).thenReturn(true);
        when(client.embedQuery(eq("Paguei no mercado"), any(Duration.class)))
                .thenThrow(new TransactionEmbeddingException(503));

        try (var enabled = retriever(true, SEARCH_BUDGET)) {
            assertThat(enabled.findExamples(userId, QUERY)).isEmpty();
        }
        assertThat(outcomeCount("failed")).isOne();
    }

    @Test
    void boundsTheWholeLookupAndCancelsSlowWork() throws InterruptedException {
        Duration budget = Duration.ofMillis(300);
        var interrupted = new CountDownLatch(1);
        when(repository.hasReady(eq(userId), eq(properties(true, budget).version()), any(Duration.class))).thenAnswer(call -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException exception) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
            return false;
        });

        try (var enabled = retriever(true, budget)) {
            assertTimeout(SEARCH_BUDGET, () -> assertThat(enabled.findExamples(userId, QUERY)).isEmpty());
            assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
        }
        verifyNoInteractions(client);
        assertThat(outcomeCount("timeout")).isOne();
    }

    private TransactionRagRetriever retriever(boolean enabled, Duration timeout) {
        var beans = new StaticListableBeanFactory(Map.of("client", client, "metrics", metrics));
        return new TransactionRagRetriever(properties(enabled, timeout), repository,
                beans.getBeanProvider(TransactionEmbeddingClient.class), beans.getBeanProvider(MeterRegistry.class));
    }

    private TransactionRagProperties properties(boolean enabled, Duration timeout) {
        return new TransactionRagProperties(enabled, Set.of(userId), "gemini-embedding-001", timeout, 0.55, 3, 100, 20);
    }

    private long outcomeCount(String outcome) {
        return metrics.get(SEARCH_METRIC).tag("outcome", outcome).timer().count();
    }
}
