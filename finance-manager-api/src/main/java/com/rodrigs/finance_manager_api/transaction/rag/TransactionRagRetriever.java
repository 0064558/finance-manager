package com.rodrigs.finance_manager_api.transaction.rag;

import com.rodrigs.finance_manager_api.transaction.rag.TransactionEmbeddingRepository.Match;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class TransactionRagRetriever implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionRagRetriever.class);
    private static final String SEARCH_METRIC = "finance.manager.chat.rag.search";
    private static final String EXAMPLES_METRIC = "finance.manager.chat.rag.examples";
    private static final String CHAT_METRIC = "finance.manager.chat.rag.suggestion";

    private final TransactionRagProperties properties;
    private final TransactionEmbeddingRepository repository;
    private final ObjectProvider<TransactionEmbeddingClient> clients;
    private final MeterRegistry metrics;
    private final Timer catalogueChat;
    private final Timer ragChat;
    // A slow provider or JDBC connection can consume a slot, so the chat never queues more work.
    private final ThreadPoolExecutor searches = new ThreadPoolExecutor(0, 4, 30, TimeUnit.SECONDS,
            new SynchronousQueue<>(), Thread.ofPlatform().daemon().name("transaction-rag-search-", 1).factory());

    public TransactionRagRetriever(TransactionRagProperties properties, TransactionEmbeddingRepository repository,
                                   ObjectProvider<TransactionEmbeddingClient> clients, ObjectProvider<MeterRegistry> meters) {
        this.properties = properties;
        this.repository = repository;
        this.clients = clients;
        this.metrics = meters.getIfAvailable();
        this.catalogueChat = chatTimer("catalogue");
        this.ragChat = chatTimer("rag");
    }

    public List<Match> findExamples(UUID userId, String input) {
        if (!properties.permits(userId)) {
            return List.of();
        }
        long started = System.nanoTime();
        String query = TransactionEmbeddingText.normalize(input);
        if (query.isEmpty()) {
            return record(started, SearchResult.empty("empty_query"));
        }
        long deadline = started + properties.searchTimeout().toNanos();

        final Future<SearchResult> search;
        try {
            search = searches.submit(() -> search(userId, query, deadline));
        } catch (RejectedExecutionException exception) {
            return record(started, SearchResult.empty("busy"));
        }

        try {
            return record(started, search.get(remainingNanos(deadline), TimeUnit.NANOSECONDS));
        } catch (TimeoutException exception) {
            search.cancel(true);
            return record(started, SearchResult.empty("timeout"));
        } catch (InterruptedException exception) {
            search.cancel(true);
            Thread.currentThread().interrupt();
            return record(started, SearchResult.empty("interrupted"));
        } catch (ExecutionException exception) {
            Throwable failure = exception.getCause();
            if (failure instanceof TimeoutException || failure instanceof QueryTimeoutException) {
                return record(started, SearchResult.empty("timeout"));
            }
            LOGGER.debug("Transaction RAG search failed ({})", failure.getClass().getSimpleName());
            return record(started, SearchResult.empty("failed"));
        }
    }

    private SearchResult search(UUID userId, String query, long deadline) throws TimeoutException {
        String version = properties.version();
        if (!repository.hasReady(userId, version, remaining(deadline))) {
            return SearchResult.empty("empty_history");
        }
        TransactionEmbeddingClient client = clients.getIfAvailable();
        if (client == null) {
            return SearchResult.empty("failed");
        }
        float[] vector = client.embedQuery(query, remaining(deadline));
        List<Match> matches = repository.search(userId, version, vector, properties.minSimilarity(),
                        properties.maxExamples(), remaining(deadline)).stream()
                .map(match -> new Match(TransactionEmbeddingText.normalize(match.description()), match.categoryId(), match.type()))
                .filter(match -> !match.description().isEmpty())
                .toList();
        return new SearchResult(matches.isEmpty() ? "no_match" : "hit", matches);
    }

    private Duration remaining(long deadline) throws TimeoutException {
        return Duration.ofNanos(remainingNanos(deadline));
    }

    private long remainingNanos(long deadline) throws TimeoutException {
        long remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
            throw new TimeoutException();
        }
        return remaining;
    }

    private List<Match> record(long started, SearchResult result) {
        if (metrics != null) {
            metrics.timer(SEARCH_METRIC, "outcome", result.outcome())
                    .record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
            if ("hit".equals(result.outcome())) {
                metrics.summary(EXAMPLES_METRIC).record(result.matches().size());
            }
        }
        return result.matches();
    }

    private Timer chatTimer(String context) {
        return metrics == null ? null : Timer.builder(CHAT_METRIC)
                .tag("context", context)
                .publishPercentiles(0.50, 0.95)
                .register(metrics);
    }

    public void recordChatLatency(long elapsedNanos, boolean historyIncluded) {
        Timer timer = historyIncluded ? ragChat : catalogueChat;
        if (timer != null) {
            timer.record(elapsedNanos, TimeUnit.NANOSECONDS);
        }
    }

    @Override
    @PreDestroy
    public void close() {
        searches.shutdownNow();
    }

    private record SearchResult(String outcome, List<Match> matches) {
        private static SearchResult empty(String outcome) {
            return new SearchResult(outcome, List.of());
        }
    }
}
