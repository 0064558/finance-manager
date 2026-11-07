package com.rodrigs.finance_manager_api.transaction.controller;

import com.rodrigs.finance_manager_api.transaction.dto.TransactionSuggestionResponseDTO.Status;
import com.rodrigs.finance_manager_api.transaction.dto.TransactionSuggestionResponseDTO.Tipo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransactionSuggestionControllerTest {

    @Test
    void sendsDescriptionLiterallyAsASeparateUserMessageAndConvertsStructuredSuggestion() {
        var model = mock(ChatModel.class);
        var controller = controllerWithModelResponse(model, """
                {"status":"SUGESTAO","descricao":"Almoço","valor":47.90,
                 "tipo":"DESPESA","categoriaSugerida":"ALIMENTACAO","mensagem":""}
                """);
        String description = "Gastei 47,90 no almoço. {anotação}";

        var suggestion = controller.suggest(new TransactionSuggestionController.SuggestionRequest(description));

        var prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(prompt.capture());
        assertThat(prompt.getValue().getInstructions()).satisfiesExactly(
                message -> assertThat(message).isInstanceOfSatisfying(SystemMessage.class,
                        system -> assertThat(system.getText())
                                .contains("Se o valor estiver ausente, preencha apenas status com ESCLARECIMENTO.")),
                message -> assertThat(message).isInstanceOfSatisfying(UserMessage.class,
                        user -> assertThat(user.getText()).isEqualTo(description))
        );
        assertThat(suggestion.status()).isEqualTo(Status.SUGESTAO);
        assertThat(suggestion.descricao()).isEqualTo("Almoço");
        assertThat(suggestion.valor()).isEqualByComparingTo("47.90");
        assertThat(suggestion.tipo()).isEqualTo(Tipo.DESPESA);
        assertThat(suggestion.categoriaSugerida()).isEqualTo("ALIMENTACAO");
        assertThat(suggestion.mensagem()).isNull();
    }

    @Test
    void convertsClarificationWithoutAnAmount() {
        var controller = controllerWithModelResponse(mock(ChatModel.class), """
                {"status":"ESCLARECIMENTO","descricao":"Almoço","valor":0,
                 "tipo":"DESPESA","categoriaSugerida":"ALIMENTACAO","mensagem":""}
                """);

        var response = controller.suggest(new TransactionSuggestionController.SuggestionRequest("Paguei o almoço."));

        assertThat(response.status()).isEqualTo(Status.ESCLARECIMENTO);
        assertThat(response.valor()).isNull();
        assertThat(response.descricao()).isNull();
        assertThat(response.tipo()).isNull();
        assertThat(response.categoriaSugerida()).isNull();
        assertThat(response.mensagem()).isEqualTo("O valor está faltando. Repita seu prompt informando também o valor.");
    }

    private TransactionSuggestionController controllerWithModelResponse(ChatModel model, String modelText) {
        when(model.getOptions()).thenReturn(GoogleGenAiChatOptions.builder().build());
        when(model.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(modelText)))));
        return new TransactionSuggestionController(ChatClient.builder(model));
    }
}
