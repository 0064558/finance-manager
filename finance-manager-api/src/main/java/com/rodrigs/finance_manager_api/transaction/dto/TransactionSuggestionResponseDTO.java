package com.rodrigs.finance_manager_api.transaction.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionSuggestionResponseDTO(
        @JsonProperty(required = true) Status status,
        String descricao,
        BigDecimal valor,
        Tipo tipo,
        String categoriaSugerida,
        String mensagem
) {
    private static final String MISSING_AMOUNT_MESSAGE =
            "O valor está faltando. Repita seu prompt informando também o valor.";

    public TransactionSuggestionResponseDTO {
        switch (status) {
            case SUGESTAO -> mensagem = null;
            case ESCLARECIMENTO -> {
                descricao = null;
                valor = null;
                tipo = null;
                categoriaSugerida = null;
                mensagem = MISSING_AMOUNT_MESSAGE;
            }
        }
    }

    public enum Status {
        SUGESTAO,
        ESCLARECIMENTO
    }

    public enum Tipo {
        RECEITA,
        DESPESA
    }
}
