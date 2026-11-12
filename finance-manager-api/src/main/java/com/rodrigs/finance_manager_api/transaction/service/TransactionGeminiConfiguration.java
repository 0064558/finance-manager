package com.rodrigs.finance_manager_api.transaction.service;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "google-genai")
class TransactionGeminiConfiguration {
    @Bean
    @ConditionalOnMissingBean(Client.class)
    Client transactionGeminiClient(Environment environment) {
        return createClient(environment.getRequiredProperty("spring.ai.google.genai.api-key"));
    }

    static Client createClient(String apiKey) {
        apiKey = apiKey.strip();
        if (apiKey.isBlank() || apiKey.startsWith("${")) {
            throw new IllegalArgumentException("Gemini API key must be configured");
        }
        return Client.builder().apiKey(apiKey).vertexAI(false).httpOptions(httpOptions()).build();
    }

    static HttpOptions httpOptions() {
        // One HTTP attempt in the SDK; the model's RetryTemplate owns the overall attempt limit.
        return HttpOptions.builder().timeout(30_000)
                .retryOptions(HttpRetryOptions.builder().attempts(1).build()).build();
    }

    @Bean
    @ConditionalOnMissingBean(RetryTemplate.class)
    RetryTemplate transactionGenerationRetryTemplate() {
        return new RetryTemplate(retryPolicy());
    }

    static RetryPolicy retryPolicy() {
        return RetryPolicy.builder().maxRetries(1).delay(Duration.ofMillis(500)).jitter(Duration.ZERO)
                .predicate(failure -> TransactionProviderFailure.from(failure).retryable()).build();
    }
}
