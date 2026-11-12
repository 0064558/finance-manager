package com.rodrigs.finance_manager_api.transaction.rag;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

final class TransactionEmbeddingWorker {
    private static final Logger LOG = LoggerFactory.getLogger(TransactionEmbeddingWorker.class);
    private static final Duration LEASE = Duration.ofSeconds(30);
    private static final Duration CONFIGURATION_RETRY_DELAY = Duration.ofMinutes(5);
    private static final String INDEXING_METRIC = "finance.manager.chat.rag.indexing";

    private final TransactionRagProperties properties;
    private final TransactionEmbeddingRepository repository;
    private final TransactionEmbeddingClient client;
    private final MeterRegistry meters;
    private Instant providerRetryAt = Instant.EPOCH;

    TransactionEmbeddingWorker(TransactionRagProperties properties, TransactionEmbeddingRepository repository,
            TransactionEmbeddingClient client, MeterRegistry meters) {
        this.properties = properties;
        this.repository = repository;
        this.client = client;
        this.meters = meters;
    }

    @Scheduled(fixedDelayString = "${finance-manager.transaction-chat.rag.indexing-delay:30000}",
            initialDelayString = "${finance-manager.transaction-chat.rag.indexing-initial-delay:30000}")
    void run() {
        if (!properties.enabled()) {
            return;
        }
        for (UUID userId : properties.pilotUserIds()) {
            if (Instant.now().isBefore(providerRetryAt)) {
                return;
            }
            try {
                indexBatch(userId);
            } catch (RuntimeException exception) {
                record("batch_failed");
                LOG.warn("Transaction embedding batch failed ({})", exception.getClass().getSimpleName());
            }
        }
    }

    private void indexBatch(UUID userId) {
        repository.reconcile(userId, properties.version(), properties.backfillLimit(), properties.batchSize());
        for (int processed = 0; processed < properties.batchSize(); processed++) {
            var claimed = repository.claim(userId, properties.version(), LEASE);
            if (claimed.isEmpty()) {
                break;
            }
            if (!index(claimed.get())) {
                break;
            }
        }
    }

    private boolean index(TransactionEmbeddingRepository.Work work) {
        String text = TransactionEmbeddingText.normalize(work.description());
        if (text.isEmpty()) {
            record(repository.skip(work) ? "skipped" : "stale");
            return true;
        }

        float[] vector;
        try {
            vector = client.embedDocument(text);
        } catch (TransactionEmbeddingException exception) {
            long seconds = Math.min(3600, 30L << Math.clamp(work.attempts() - 1, 0, 7));
            Duration delay = exception.configurationFailure() ? CONFIGURATION_RETRY_DELAY : Duration.ofSeconds(seconds);
            boolean pause = exception.configurationFailure() || exception.statusCode().orElse(0) == 429;
            if (pause) {
                providerRetryAt = Instant.now().plus(delay);
            }
            record(repository.retry(work, delay) ? "retry" : "stale");
            if (exception.statusCode().isPresent()) {
                LOG.warn("Transaction embedding unavailable (HTTP {})", exception.statusCode().getAsInt());
            } else {
                LOG.warn("Transaction embedding unavailable");
            }
            return !pause;
        }
        record(repository.complete(work, vector) ? "indexed" : "stale");
        return true;
    }

    private void record(String outcome) {
        meters.counter(INDEXING_METRIC, "outcome", outcome).increment();
    }
}
