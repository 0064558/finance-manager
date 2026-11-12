package com.rodrigs.finance_manager_api.transaction.rag;

import com.rodrigs.finance_manager_api.transaction.rag.TransactionEmbeddingRepository.Coverage;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.util.function.ToDoubleFunction;

final class TransactionEmbeddingMetrics {
    private static final Logger LOG = LoggerFactory.getLogger(TransactionEmbeddingMetrics.class);
    private final TransactionRagProperties properties;
    private final TransactionEmbeddingRepository repository;
    private final MeterRegistry meters;
    private volatile Snapshot snapshot;

    TransactionEmbeddingMetrics(TransactionRagProperties properties, TransactionEmbeddingRepository repository,
                                MeterRegistry meters) {
        this.properties = properties;
        this.repository = repository;
        this.meters = meters;
        vectors("ready", Coverage::ready);
        vectors("pending", Coverage::pending);
        vectors("skipped", Coverage::skipped);
        Gauge.builder("finance.manager.chat.rag.coverage", this, metrics -> metrics.value(Coverage::ratio))
                .description("Fraction of current vectors among non-skipped transactions in the pilot cohort")
                .register(meters);
        Gauge.builder("finance.manager.chat.rag.coverage.updated", this, metrics -> {
            Snapshot current = metrics.snapshot;
            return current == null ? Double.NaN : current.updatedAt();
        }).baseUnit("seconds").description("Unix timestamp of the last successful coverage refresh").register(meters);
    }

    private void vectors(String state, ToDoubleFunction<Coverage> value) {
        Gauge.builder("finance.manager.chat.rag.vectors", this, metrics -> metrics.value(value))
                .tag("state", state).register(meters);
    }

    private double value(ToDoubleFunction<Coverage> value) {
        Snapshot current = snapshot;
        return current == null ? Double.NaN : value.applyAsDouble(current.coverage());
    }

    @Scheduled(fixedDelayString = "${finance-manager.transaction-chat.rag.coverage-delay:30000}")
    void refresh() {
        if (!properties.enabled()) {
            return;
        }
        try {
            snapshot = new Snapshot(repository.coverage(properties.pilotUserIds(), properties.version()),
                    Instant.now().getEpochSecond());
        } catch (RuntimeException exception) {
            meters.counter("finance.manager.chat.rag.coverage.failures").increment();
            LOG.warn("Transaction embedding coverage refresh failed ({})", exception.getClass().getSimpleName());
        }
    }

    private record Snapshot(Coverage coverage, long updatedAt) {}
}
