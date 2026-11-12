package com.rodrigs.finance_manager_api.transaction.service;

import com.google.genai.Client;
import com.rodrigs.finance_manager_api.category.service.CategoryService;
import com.rodrigs.finance_manager_api.shared.exception.TransactionSuggestionUnavailableException;
import com.rodrigs.finance_manager_api.transaction.dto.TransactionSuggestionResponseDTO;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.core.retry.RetryTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@EnabledIfSystemProperty(named = "transaction.suggestions.simulation.url", matches = "http://(localhost|127\\.0\\.0\\.1):[0-9]+/gemini")
class TransactionGeminiSimulationTest {
    @Test
    void recoversFromOne503UsingTheRealSdkAndProductionPolicy() throws Exception {
        exercise("recover", 2, false);
    }

    @Test
    void recoversFromAnInterruptedConnection() throws Exception {
        exercise("disconnect", 2, false);
    }

    @ParameterizedTest
    @CsvSource({"unavailable,2", "rate-limited,1", "invalid,1", "slow,2"})
    void boundsRetriesAndPropagatesAControlledFailure(String scenario, int attempts) throws Exception {
        exercise(scenario, attempts, true);
    }

    private void exercise(String scenario, int attempts, boolean failure) throws Exception {
        String baseUrl = System.getProperty("transaction.suggestions.simulation.url");
        String modelName = scenario + "-" + UUID.randomUUID();
        var options = TransactionGeminiConfiguration.httpOptions().toBuilder().baseUrl(baseUrl)
                .timeout(scenario.equals("slow") ? 200 : 5_000).build();
        try (Client client = Client.builder().apiKey("unused-simulation-key").vertexAI(false).httpOptions(options).build();
             var validators = Validation.buildDefaultValidatorFactory()) {
            var model = GoogleGenAiChatModel.builder().genAiClient(client)
                    .options(GoogleGenAiChatOptions.builder().model(modelName).build())
                    .retryTemplate(new RetryTemplate(TransactionGeminiConfiguration.retryPolicy())).build();
            var service = new TransactionSuggestionService(ChatClient.builder(model), mock(CategoryService.class),
                    validators.getValidator());
            var messages = List.of(new TransactionSuggestionService.ChatMessage("Gastei R$ 47,90 no almoço.", LocalDate.now()));
            if (failure) {
                assertThatThrownBy(() -> service.suggestChat(UUID.randomUUID(), messages))
                        .isInstanceOf(TransactionSuggestionUnavailableException.class).hasNoCause();
            } else {
                assertThat(service.suggestChat(UUID.randomUUID(), messages)).singleElement().satisfies(draft -> {
                    assertThat(draft.status()).isEqualTo(TransactionSuggestionResponseDTO.Status.SUGESTAO);
                    assertThat(draft.valor()).isEqualByComparingTo("47.90");
                    assertThat(draft.tipo()).isEqualTo(TransactionSuggestionResponseDTO.Tipo.DESPESA);
                });
            }
        }
        try (var http = HttpClient.newHttpClient()) {
            var reply = http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/__requests/" + modelName)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(reply.statusCode()).isEqualTo(200);
            assertThat(JsonMapper.builder().build().readTree(reply.body()).path("count").asInt()).isEqualTo(attempts);
        }
    }
}
