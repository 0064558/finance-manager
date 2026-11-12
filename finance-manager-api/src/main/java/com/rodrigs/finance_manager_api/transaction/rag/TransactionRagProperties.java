package com.rodrigs.finance_manager_api.transaction.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@ConfigurationProperties("finance-manager.transaction-chat.rag")
public record TransactionRagProperties(
        @DefaultValue("false") boolean enabled,
        Set<UUID> pilotUserIds,
        @DefaultValue("gemini-embedding-001") String model,
        @DefaultValue("2s") Duration searchTimeout,
        @DefaultValue("0.55") double minSimilarity,
        @DefaultValue("3") int maxExamples,
        @DefaultValue("100") int backfillLimit,
        @DefaultValue("20") int batchSize
) {
    public TransactionRagProperties {
        pilotUserIds = pilotUserIds == null ? Set.of() : Set.copyOf(pilotUserIds);
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("RAG embedding model must be configured");
        }
        if (searchTimeout == null || searchTimeout.toMillis() < 1) {
            throw new IllegalArgumentException("RAG search timeout must be at least one millisecond");
        }
        if (!Double.isFinite(minSimilarity) || minSimilarity < 0 || minSimilarity > 1) {
            throw new IllegalArgumentException("RAG minimum cosine similarity must be between 0 and 1");
        }
        if (maxExamples < 1 || maxExamples > 3 || backfillLimit < 0 || batchSize < 1) {
            throw new IllegalArgumentException("Invalid RAG example, backfill or batch limit");
        }
    }

    public boolean permits(UUID userId) {
        return enabled && pilotUserIds.contains(userId);
    }

    public String version() {
        return model + ":768:normalized-v1";
    }
}
