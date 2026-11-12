package com.rodrigs.finance_manager_api.transaction.rag;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "finance-manager.transaction-chat.rag", name = "enabled", havingValue = "true")
class TransactionEmbeddingConfiguration {
    @Bean
    @ConditionalOnMissingBean(TransactionEmbeddingClient.class)
    GeminiTransactionEmbeddingClient transactionEmbeddingClient(
            @Value("${spring.ai.google.genai.api-key:}") String apiKey, TransactionRagProperties properties) {
        return GeminiTransactionEmbeddingClient.create(apiKey, properties.model());
    }

    @Bean
    TransactionEmbeddingWorker transactionEmbeddingWorker(TransactionRagProperties properties,
            TransactionEmbeddingRepository repository, TransactionEmbeddingClient client, MeterRegistry meters) {
        return new TransactionEmbeddingWorker(properties, repository, client, meters);
    }
}
