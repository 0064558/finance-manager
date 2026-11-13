package com.rodrigs.finance_manager_api.transaction.rag;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.UUID;

final class TransactionEmbeddingWorker {
    private static final Logger LOG = LoggerFactory.getLogger(TransactionEmbeddingWorker.class);
    private static final Duration LEASE = Duration.ofSeconds(30);
    private static final String INDEXING_METRIC = "finance.manager.chat.rag.indexing";

    private final TransactionRagProperties properties;
    private final TransactionEmbeddingRepository repository;
    private final TransactionEmbeddingClient client;
    private final MeterRegistry meters;

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
            index(claimed.get());
        }
    }

    private void index(TransactionEmbeddingRepository.Work work) {
        String text = TransactionEmbeddingText.normalize(work.description());
        if (text.isEmpty()) {
            record(repository.skip(work) ? "skipped" : "stale");
            return;
        }
        float[] vector;
        try {
            vector = client.embedDocument(text);
        } catch (RuntimeException exception) {
            long seconds = Math.min(3600, 30L << Math.clamp(work.attempts() - 1, 0, 7));
            record(repository.retry(work, Duration.ofSeconds(seconds)) ? "retry" : "stale");
            LOG.warn("Local transaction embedding failed ({})", exception.getClass().getSimpleName());
            return;
        }
        if (TransactionEmbeddingClient.hasFeatures(vector)) {
            record(repository.complete(work, vector) ? "indexed" : "stale");
        } else {
            record(repository.skip(work) ? "skipped" : "stale");
        }
    }

    private void record(String outcome) {
        meters.counter(INDEXING_METRIC, "outcome", outcome).increment();
    }
}
