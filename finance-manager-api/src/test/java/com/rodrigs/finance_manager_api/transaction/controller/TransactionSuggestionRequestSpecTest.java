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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@TestPropertySource(properties = {
        "spring.ai.model.chat=google-genai",
        "spring.ai.google.genai.api-key=unused-for-mock"
})
class TransactionSuggestionRequestSpecTest extends HttpRequestSpec {

    private static final String SUGGESTIONS = TRANSACTIONS + "/suggestions";
    private static final Map<String, String> LUNCH = Map.of("description", "Gastei R$ 47,90 no almoço.");

    @MockitoBean(enforceOverride = true)
    private ChatModel chatModel;

    @Test
    void scopesCategoryToTheAuthenticatedUserAndDoesNotCreateTransactions() throws Exception {
        var owner = user();
        var other = user();
        String expenseCategory = category(owner, "Alimentação", "EXPENSE");
        category(other, "Alimentação", "INCOME");
        when(chatModel.getOptions()).thenReturn(GoogleGenAiChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(
                new AssistantMessage("""
                        {"status":"SUGESTAO","descricao":"Almoço","valor":47.90,
                         "tipo":"DESPESA","categoriaSugerida":"ALIMENTACAO"}
                        """)))));

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

        verify(chatModel, never()).call(any(Prompt.class));
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
}
