package com.rodrigs.finance_manager_api.transaction.service;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in diagnostic with a fixed, non-financial input. Never prints credentials. */
@EnabledIfSystemProperty(named = "transaction.suggestions.diagnose-provider", matches = "true")
class GeminiProviderDiagnosticTest {
    @Test
    void checksGenerationWithTheConfiguredSdkOptions() {
        String key = System.getenv("GEMINI_API_KEY");
        assertThat(key).as("GEMINI_API_KEY must be configured").isNotBlank();
        try (Client client = TransactionGeminiConfiguration.createClient(key)) {
            try {
                var response = client.models.generateContent("gemini-3.1-flash-lite", "Reply with OK.", null);
                assertThat(response.text()).isNotBlank();
                System.out.println("Gemini diagnostic: generation succeeded with the shared SDK options.");
            } catch (ApiException failure) {
                // Only this fixed public input is used; replace the key before printing a bounded diagnostic.
                String detail = failure.message().replace(key, "[redacted]").replaceAll("[\\r\\n]", " ");
                System.out.printf("Gemini diagnostic: status=%d, detail=%s%n", failure.code(),
                        detail.substring(0, Math.min(detail.length(), 500)));
                throw new AssertionError("Gemini fixed-input diagnostic failed; see the sanitized status above.");
            }
        }
    }
}
