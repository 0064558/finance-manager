package com.rodrigs.finance_manager_api.transaction.controller;

import com.rodrigs.finance_manager_api.transaction.dto.TransactionSuggestionResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions/suggestions")
@SecurityRequirement(name = "bearerAuth")
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "google-genai")
public class TransactionSuggestionController {

    private static final String SYSTEM_INSTRUCTION = """
            Interprete a mensagem do usuário como uma descrição de lançamento financeiro.
            Para uma sugestão, preencha status com SUGESTAO e descricao com um rótulo curto em
            português com acentuação, como "Corrida de táxi". Deixe o tipo e o valor em seus
            próprios campos: valor recebe o número informado, tipo recebe RECEITA ou DESPESA.
            Em categoriaSugerida, use um rótulo em maiúsculas sem acentos, como ALIMENTACAO.
            Use apenas os dados informados para descricao, valor e tipo. Nunca invente um valor.
            Se o valor estiver ausente, preencha apenas status com ESCLARECIMENTO.
            A mensagem ao usuário é gerada pelo DindDim. Omita o campo mensagem.
            """;

    private final ChatClient chatClient;

    public TransactionSuggestionController(ChatClient.Builder builder) {
        this.chatClient = builder.defaultSystem(SYSTEM_INSTRUCTION).build();
    }

    @Operation(summary = "Gera uma sugestão de transação estruturada para revisão")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public TransactionSuggestionResponseDTO suggest(@Valid @RequestBody SuggestionRequest request) {
        return chatClient.prompt()
                .messages(new UserMessage(request.description()))
                .call()
                .entity(TransactionSuggestionResponseDTO.class, spec -> spec.useProviderStructuredOutput());
    }

    public record SuggestionRequest(@NotBlank @Size(max = 500) String description) {
    }
}
