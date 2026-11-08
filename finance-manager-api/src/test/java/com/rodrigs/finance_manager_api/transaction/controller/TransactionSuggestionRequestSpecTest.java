package com.rodrigs.finance_manager_api.transaction.controller;

import com.rodrigs.finance_manager_api.support.HttpRequestSpec;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@TestPropertySource(properties = {
        "spring.ai.model.chat=google-genai",
        "spring.ai.google.genai.api-key=unused-for-mock"
})
class TransactionSuggestionRequestSpecTest extends HttpRequestSpec {

    private static final String SUGGESTIONS = TRANSACTIONS + "/suggestions";
    private static final String CHAT = TRANSACTIONS + "/chat";
    private static final Map<String, String> LUNCH = Map.of("description", "Gastei R$ 47,90 no almoço.");

    @MockitoBean(enforceOverride = true)
    private ChatModel chatModel;

    @Test
    void scopesCategoryToTheAuthenticatedUserAndDoesNotCreateTransactions() throws Exception {
        var owner = user();
        var other = user();
        String expenseCategory = category(owner, "Alimentação", "EXPENSE");
        category(other, "Alimentação", "INCOME");
        mockLunchSuggestion();

        Reply owned = request("POST", SUGGESTIONS, owner.token(), LUNCH);
        owned.status(200);
        assertThat(owned.text("status")).isEqualTo("SUGESTAO");
        assertThat(owned.text("categoriaId")).isEqualTo(expenseCategory);
        owned.money("valor", "47.90");

        Reply foreign = request("POST", SUGGESTIONS, other.token(), LUNCH);
        foreign.status(200);
        assertThat(foreign.text("status")).isEqualTo("SUGESTAO");
        assertThat(foreign.value("categoriaId")).isNull();
        assertThat(foreign.value("categoriaSugerida")).isNull();

        assertNoTransactions(owner);
        assertNoTransactions(other);
    }

    @Test
    void requiresAuthenticationBeforeCallingTheProvider() throws Exception {
        request("POST", SUGGESTIONS, null, LUNCH).status(401);
        request("GET", CHAT, null, null).status(401);
        request("POST", CHAT, null, LUNCH).status(401);
        request("DELETE", CHAT + "/drafts/" + UUID.randomUUID(), null, null).status(401);

        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void restoresOnlyTheUsersChatAfterLoginAndPreservesConfirmation() throws Exception {
        var owner = user();
        var other = user();
        String accountId = account(owner, "Conta de teste", "100");
        String categoryId = category(owner, "Alimentação", "EXPENSE");
        when(chatModel.getOptions()).thenReturn(GoogleGenAiChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(missingValue(), chatLunch("47.90"));

        Reply clarification = request("POST", CHAT, owner.token(),
                Map.of("description", "Paguei o ônibus."));
        clarification.status(200);
        assertThat(clarification.text("response.motivo")).isEqualTo("VALOR_AUSENTE");
        Reply suggestion = request("POST", CHAT, owner.token(), LUNCH);
        suggestion.status(200);
        assertThat(suggestion.value("saved")).isEqualTo(false);
        assertThat(suggestion.text("drafts.0.id")).isEqualTo(suggestion.text("id"));
        assertNoTransactions(owner);

        var body = transactionBody(accountId, categoryId, "EXPENSE", "49.90", LocalDate.now().minusDays(1));
        Reply created = requestWithKey("POST", TRANSACTIONS, owner.token(), body, suggestion.text("id"));
        created.status(201);

        Reply login = request("POST", AUTH + "/login", null, credentials(owner.email(), PASSWORD));
        login.status(200);
        String newToken = login.text("accessToken");
        Reply history = request("GET", CHAT, newToken, null);
        history.status(200);
        assertThat(history.list("")).hasSize(2);
        assertThat(history.text("0.id")).isEqualTo(clarification.text("id"));
        assertThat(history.text("1.prompt")).isEqualTo(LUNCH.get("description"));
        assertThat(history.text("1.response.descricao")).isEqualTo("Almoço");
        assertThat(history.value("1.saved")).isEqualTo(true);
        assertThat(history.value("1.drafts.0.saved")).isEqualTo(true);

        Reply retry = requestWithKey("POST", TRANSACTIONS, newToken, body, suggestion.text("id"));
        retry.status(201);
        assertThat(retry.text("id")).isEqualTo(created.text("id"));
        assertThat(request("GET", TRANSACTIONS, newToken, null).list("content")).hasSize(1);

        Reply otherHistory = request("GET", CHAT, other.token(), null);
        otherHistory.status(200);
        assertThat(otherHistory.list("")).isEmpty();
    }

    @Test
    void keepsTheOriginalDateAcrossClarificationAndCorrectionWithoutCreatingDuplicateDrafts() throws Exception {
        var owner = user();
        var other = user();
        String accountId = account(owner, "Conta de teste", "100");
        String categoryId = category(owner, "Alimentação", "EXPENSE");
        when(chatModel.getOptions()).thenReturn(GoogleGenAiChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(missingValue(), chatLunch("47.90"), chatLunch("42.90"));

        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        Reply question = request("POST", CHAT, owner.token(),
                Map.of("description", "Paguei o almoço ontem", "timeZone", "UTC"));
        question.status(200);
        assertThat(question.text("response.motivo")).isEqualTo("VALOR_AUSENTE");
        String questionId = question.text("id");
        var answer = Map.of("description", "47,90", "timeZone", "UTC", "replyTo", questionId,
                "requestId", UUID.randomUUID().toString());
        request("POST", CHAT, other.token(), answer).status(404);
        Reply suggestion = request("POST", CHAT, owner.token(), answer);
        suggestion.status(200);
        String originalId = suggestion.text("id");
        assertThat(suggestion.text("replyTo")).isEqualTo(questionId);
        assertThat(suggestion.text("response.descricao")).isEqualTo("Almoço");
        String suggestedDate = suggestion.text("response.data");
        assertThat(LocalDate.parse(suggestedDate)).isBetween(yesterday, LocalDate.now(ZoneOffset.UTC).minusDays(1));
        suggestion.money("response.valor", "47.90");
        assertThat(request("POST", CHAT, owner.token(), answer).text("id")).isEqualTo(originalId);
        var reusedRequestId = Map.of("description", "49,90", "replyTo", questionId, "requestId", answer.get("requestId"));
        Reply conflict = request("POST", CHAT, owner.token(), reusedRequestId);
        conflict.status(409);
        assertThat(conflict.text("code")).isEqualTo("CHAT_REQUEST_CONFLICT");

        Reply correction = request("POST", CHAT, owner.token(),
                Map.of("description", "Na verdade, foi 42,90", "timeZone", "UTC", "replyTo", originalId));
        correction.status(200);
        assertThat(correction.text("response.data")).isEqualTo(suggestedDate);
        correction.money("response.valor", "42.90");
        verify(chatModel, times(3)).call(any(Prompt.class));
        assertNoTransactions(owner);

        var body = transactionBody(accountId, categoryId, "EXPENSE", "42.90", LocalDate.parse(suggestedDate));
        Reply staleConfirmation = requestWithKey("POST", TRANSACTIONS, owner.token(), body, originalId);
        staleConfirmation.status(409);
        assertThat(staleConfirmation.text("code")).isEqualTo("CHAT_DRAFT_UNAVAILABLE");
        requestWithKey("POST", TRANSACTIONS, owner.token(), body, correction.text("id")).status(201);

        Reply history = request("GET", CHAT, owner.token(), null);
        history.status(200);
        assertThat(history.list("")).hasSize(3);
        assertThat(history.value("0.superseded")).isEqualTo(true);
        assertThat(history.value("1.superseded")).isEqualTo(true);
        assertThat(history.value("2.drafts.0.saved")).isEqualTo(true);
        assertThat(request("GET", TRANSACTIONS, owner.token(), null).list("content")).hasSize(1);
    }

    @Test
    void discardsAndConfirmsDraftsFromTheSameMessageIndependently() throws Exception {
        var owner = user();
        var other = user();
        String accountId = account(owner, "Conta de teste", "100");
        String categoryId = category(owner, "Alimentação", "EXPENSE");
        when(chatModel.getOptions()).thenReturn(GoogleGenAiChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(providerResponse("""
                {"status":"SUGESTAO","lancamentos":[
                    {"trecho":"R$ 20 no almoço","descricao":"Almoço","valor":20,"tipo":"DESPESA",
                     "categoriaSugerida":"ALIMENTACAO"},
                    {"trecho":"R$ 8 no ônibus","descricao":"Ônibus","valor":8,"tipo":"DESPESA",
                     "categoriaSugerida":"TRANSPORTE"}
                ]}
                """));

        var message = Map.of("description", "Gastei R$ 20 no almoço e R$ 8 no ônibus.",
                "requestId", UUID.randomUUID().toString());
        Reply batch = request("POST", CHAT, owner.token(), message);
        batch.status(200);
        assertThat(batch.list("drafts")).hasSize(2);
        assertThat(batch.value("response")).isNull();
        String lunchId = batch.text("drafts.0.id");
        String busId = batch.text("drafts.1.id");
        assertThat(lunchId).isNotEqualTo(busId);
        assertNoTransactions(owner);

        request("DELETE", CHAT + "/drafts/" + busId, other.token(), null).status(404);
        request("DELETE", CHAT + "/drafts/" + busId, owner.token(), null).status(204);
        request("DELETE", CHAT + "/drafts/" + busId, owner.token(), null).status(204);
        var body = transactionBody(accountId, categoryId, "EXPENSE", "20", LocalDate.now().minusDays(1));
        Reply discarded = requestWithKey("POST", TRANSACTIONS, owner.token(), body, busId);
        discarded.status(409);
        assertThat(discarded.text("code")).isEqualTo("CHAT_DRAFT_UNAVAILABLE");
        requestWithKey("POST", TRANSACTIONS, owner.token(), body, lunchId).status(201);

        Reply replay = request("POST", CHAT, owner.token(), message);
        replay.status(200);
        assertThat(replay.text("id")).isEqualTo(batch.text("id"));
        assertThat(replay.text("drafts.0.id")).isEqualTo(lunchId);
        assertThat(replay.value("drafts.0.saved")).isEqualTo(true);
        assertThat(replay.text("drafts.1.id")).isEqualTo(busId);
        assertThat(replay.value("drafts.1.discarded")).isEqualTo(true);
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    void serializesConcurrentConfirmationAndDiscardOfOneDraft() throws Exception {
        var owner = user();
        String accountId = account(owner, "Conta de teste", "100");
        String categoryId = category(owner, "Alimentação", "EXPENSE");
        when(chatModel.getOptions()).thenReturn(GoogleGenAiChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(chatLunch("47.90"));

        Reply suggestion = request("POST", CHAT, owner.token(), LUNCH);
        suggestion.status(200);
        String id = suggestion.text("id");
        var body = transactionBody(accountId, categoryId, "EXPENSE", "47.90", LocalDate.now().minusDays(1));
        var attempts = simultaneously(
                () -> requestWithKey("POST", TRANSACTIONS, owner.token(), body, id),
                () -> request("DELETE", CHAT + "/drafts/" + id, owner.token(), null));
        boolean saved = attempts.getFirst().code() == 201;
        attempts.getFirst().status(saved ? 201 : 409);
        attempts.getLast().status(saved ? 409 : 204);
        assertThat(attempts.get(saved ? 1 : 0).text("code")).isEqualTo("CHAT_DRAFT_UNAVAILABLE");
        Reply history = request("GET", CHAT, owner.token(), null);
        assertThat(history.value("0.drafts.0.saved")).isEqualTo(saved);
        assertThat(history.value("0.drafts.0.discarded")).isEqualTo(!saved);
        assertThat(request("GET", TRANSACTIONS, owner.token(), null).list("content")).hasSize(saved ? 1 : 0);
    }

    @Test
    void asksForOneTransactionAtATimeWhenTwoExpensesHaveTheirOwnPrices() throws Exception {
        var user = user();

        Reply reply = request("POST", SUGGESTIONS, user.token(),
                Map.of("description", "Gastei R$ 20 no almoço e R$ 8 no ônibus."));

        reply.status(200);
        assertThat(reply.text("status")).isEqualTo("ESCLARECIMENTO");
        assertThat(reply.text("motivo")).isEqualTo("MULTIPLAS_TRANSACOES");
        assertThat(reply.text("mensagem")).isEqualTo("Envie um lançamento por vez para que eu possa sugerir os dados.");
        assertThat(reply.value("valor")).isNull();
        verify(chatModel, never()).call(any(Prompt.class));
        assertNoTransactions(user);
    }

    @Test
    void providerFailureReturnsSanitizedProblemWithoutCreatingTransactions() throws Exception {
        var user = user();
        when(chatModel.getOptions()).thenReturn(GoogleGenAiChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("sensitive provider details"));

        Reply response = request("POST", SUGGESTIONS, user.token(), LUNCH);

        response.status(502);
        assertThat(response.text("code")).isEqualTo("TRANSACTION_SUGGESTION_UNAVAILABLE");
        assertThat(response.body()).doesNotContain("sensitive provider details");
        assertNoTransactions(user);
    }

    private void assertNoTransactions(UserSession user) throws Exception {
        Reply transactions = request("GET", TRANSACTIONS, user.token(), null);
        transactions.status(200);
        assertThat(transactions.value("totalElements")).isEqualTo(0);
    }

    private void mockLunchSuggestion() {
        when(chatModel.getOptions()).thenReturn(GoogleGenAiChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(providerResponse("""
                        {"status":"SUGESTAO","descricao":"Almoço","valor":47.90,
                         "tipo":"DESPESA","categoriaSugerida":"ALIMENTACAO"}
                        """));
    }

    private ChatResponse missingValue() {
        return providerResponse("""
                {"status":"ESCLARECIMENTO","motivo":"VALOR_AUSENTE"}
                """);
    }

    private ChatResponse chatLunch(String amount) {
        return providerResponse("""
                {"status":"SUGESTAO","lancamentos":[
                    {"descricao":"Almoço","valor":%s,"tipo":"DESPESA","categoriaSugerida":"ALIMENTACAO"}
                ]}
                """.formatted(amount));
    }

    private ChatResponse providerResponse(String body) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(body))));
    }
}
