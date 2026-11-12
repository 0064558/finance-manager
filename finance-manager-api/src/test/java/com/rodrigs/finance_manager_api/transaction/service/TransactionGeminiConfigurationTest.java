package com.rodrigs.finance_manager_api.transaction.service;

import com.google.genai.Client;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionGeminiConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(TransactionGeminiConfiguration.class);

    @Test
    void usesTheResolvedCredentialFromTheEnvironment() {
        context.withPropertyValues("spring.ai.model.chat=google-genai", "GEMINI_API_KEY=fixture-key",
                        "spring.ai.google.genai.api-key=${GEMINI_API_KEY}")
                .run(application -> {
                    assertThat(application).hasSingleBean(Client.class);
                    assertThat(application).hasSingleBean(org.springframework.core.retry.RetryTemplate.class);
                    assertThat(application.getBean(Client.class).apiKey()).isEqualTo("fixture-key");
                });
    }

    @Test
    void keepsTheClientInactiveWithoutTheProviderAndRejectsUnresolvedCredentials() {
        context.withPropertyValues("spring.ai.model.chat=none").run(application ->
                assertThat(application).doesNotHaveBean(Client.class));
        context.withPropertyValues("spring.ai.model.chat=google-genai", "spring.ai.google.genai.api-key=")
                .run(application -> assertThat(application).hasFailed());
    }
}
