package com.rodrigs.finance_manager_api.transaction.controller;

import com.rodrigs.finance_manager_api.support.HttpRequestSpec;
import com.rodrigs.finance_manager_api.transaction.rag.TransactionEmbeddingClient;
import com.rodrigs.finance_manager_api.transaction.rag.TransactionEmbeddingRepository;
import com.rodrigs.finance_manager_api.transaction.rag.TransactionRagProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@TestPropertySource(properties = {
        "spring.ai.model.chat=google-genai",
        "spring.ai.google.genai.api-key=unused-for-mock",
        "finance-manager.transaction-chat.rag.enabled=true",
        "finance-manager.transaction-chat.rag.pilot-user-ids=176ad42d-98e9-489d-be3a-ab77cbdf46af",
        "finance-manager.transaction-chat.rag.indexing-initial-delay=3600000"
})
class TransactionChatRagRequestSpecTest extends HttpRequestSpec {
    private static final UUID PILOT_ID = UUID.fromString("176ad42d-98e9-489d-be3a-ab77cbdf46af");
    private static final String CHAT = TRANSACTIONS + "/chat";
    private static final String DESCRIPTION = "Paguei R$ 31,50 de Uber para o plantão.";
    private static final String OWN_HISTORY = "Corrida de Uber para o plantão com recibo de pessoa@example.test";
    private static final String FOREIGN_HISTORY = "Corrida particular de outro usuário";
    private static final String CONFIRMATION_METER = "finance.manager.chat.category.confirmation";

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private TransactionEmbeddingRepository embeddings;
    @Autowired
    private TransactionRagProperties settings;
    @Autowired
    private MeterRegistry meters;

    @MockitoBean(enforceOverride = true)
    private ChatModel chatModel;
    @MockitoBean
    private TransactionEmbeddingClient embeddingClient;

    @Test
    void usesOnlyTheAuthenticatedUsersConfirmedHistoryAndKeepsConfirmationIdempotent() throws Exception {
        String token = pilotToken();
        var own = indexHistory(PILOT_ID, token, "Plantão", OWN_HISTORY);
        var foreign = user();
        indexHistory(UUID.fromString(foreign.id()), foreign.token(), "Lazer", FOREIGN_HISTORY);
        when(embeddingClient.embedQuery(anyString(), any(Duration.class))).thenReturn(vector());
        mockDraft(own.categoryId());

        var payload = Map.of("description", DESCRIPTION, "requestId", UUID.randomUUID().toString());
        Reply suggestion = request("POST", CHAT, token, payload);
        suggestion.status(200);
        assertThat(suggestion.text("response.categoriaId")).isEqualTo(own.categoryId());
        suggestion.money("response.valor", "31.50");
        assertThat(request("POST", CHAT, token, payload).text("id")).isEqualTo(suggestion.text("id"));

        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        var sent = JSON.readTree(userPrompt(prompt.getValue()));
        assertThat(sent.get("historico").size()).isEqualTo(1);
        assertThat(sent.get("historico").get(0).get("categoriaId").asString()).isEqualTo(own.categoryId());
        assertThat(sent.get("historico").get(0).get("descricao").asString())
                .contains("Corrida de Uber para o plantão").doesNotContain("pessoa@example.test");
        assertThat(sent.toString()).doesNotContain(FOREIGN_HISTORY);
        var query = ArgumentCaptor.forClass(String.class);
        verify(embeddingClient).embedQuery(query.capture(), any(Duration.class));
        assertThat(query.getValue()).contains("Uber").doesNotContain("31,50");

        var body = transactionBody(own.accountId(), own.categoryId(), "EXPENSE", "31.50", LocalDate.now().minusDays(1));
        double retainedBefore = retainedCategories();
        Reply created = requestWithKey("POST", TRANSACTIONS, token, body, suggestion.text("id"));
        created.status(201);
        Reply replay = requestWithKey("POST", TRANSACTIONS, token, body, suggestion.text("id"));
        replay.status(201);
        assertThat(replay.text("id")).isEqualTo(created.text("id"));
        assertThat(retainedCategories()).isEqualTo(retainedBefore + 1);
    }

    @Test
    void fallsBackToTheCatalogueWhenQueryEmbeddingFails() throws Exception {
        String token = pilotToken();
        var own = indexHistory(PILOT_ID, token, "Plantão", OWN_HISTORY);
        when(embeddingClient.embedQuery(anyString(), any(Duration.class)))
                .thenThrow(new IllegalStateException("Fixture de indisponibilidade"));
        mockDraft(own.categoryId());

        Reply suggestion = request("POST", CHAT, token, Map.of("description", DESCRIPTION));
        suggestion.status(200);
        assertThat(suggestion.text("response.categoriaId")).isEqualTo(own.categoryId());
        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        var sent = JSON.readTree(userPrompt(prompt.getValue()));
        assertThat(sent.get("historico").size()).isZero();
        assertThat(sent.get("categorias").get(0).get("id").asString()).isEqualTo(own.categoryId());
        verify(embeddingClient, times(1)).embedQuery(anyString(), any(Duration.class));
    }

    private String pilotToken() throws Exception {
        String email = "pilot@example.test";
        jdbc.update("INSERT INTO users (id, name, email, password_hash) VALUES (?, 'Piloto', ?, ?)",
                PILOT_ID, email, passwordEncoder.encode(PASSWORD));
        Reply login = request("POST", AUTH + "/login", null, credentials(email, PASSWORD));
        login.status(200);
        return login.text("accessToken");
    }

    private History indexHistory(UUID userId, String token, String category, String description) throws Exception {
        Reply account = request("POST", ACCOUNTS, token, accountBody("Conta", "CHECKING", "0"));
        account.status(201);
        Reply classification = request("POST", CATEGORIES, token, categoryBody(category, "EXPENSE"));
        classification.status(201);
        String accountId = account.text("id");
        String categoryId = classification.text("id");
        var body = new HashMap<>(transactionBody(accountId, categoryId, "EXPENSE", "30", LocalDate.now().minusDays(1)));
        body.put("description", description);
        Reply transaction = request("POST", TRANSACTIONS, token, body);
        transaction.status(201);

        String version = settings.version();
        embeddings.reconcile(userId, version, settings.backfillLimit(), settings.batchSize());
        var work = embeddings.claim(userId, version, Duration.ofMinutes(1)).orElseThrow();
        assertThat(work.transactionId().toString()).isEqualTo(transaction.text("id"));
        assertThat(embeddings.complete(work, vector())).isTrue();
        return new History(accountId, categoryId);
    }

    private void mockDraft(String categoryId) {
        when(chatModel.getOptions()).thenReturn(GoogleGenAiChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("""
                {"status":"SUGESTAO","lancamentos":[
                  {"descricao":"Uber para o plantão","valor":31.50,"tipo":"DESPESA","categoriaSugerida":"%s"}
                ]}
                """.formatted(categoryId))))));
    }

    private String userPrompt(Prompt prompt) {
        return prompt.getInstructions().stream().filter(UserMessage.class::isInstance)
                .map(UserMessage.class::cast).findFirst().orElseThrow().getText();
    }

    private double retainedCategories() {
        var counter = meters.find(CONFIRMATION_METER).tag("outcome", "retained").counter();
        return counter == null ? 0 : counter.count();
    }

    private float[] vector() {
        float[] vector = new float[768];
        vector[0] = 1;
        return vector;
    }

    private record History(String accountId, String categoryId) {}
}
