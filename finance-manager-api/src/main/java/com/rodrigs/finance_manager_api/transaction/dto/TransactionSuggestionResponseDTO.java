package com.rodrigs.finance_manager_api.transaction.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rodrigs.finance_manager_api.shared.enums.TransactionType;

import java.math.BigDecimal;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionSuggestionResponseDTO(
        Status status,
        String descricao,
        BigDecimal valor,
        Tipo tipo,
        String categoriaSugerida,
        UUID categoriaId,
        Motivo motivo,
        String mensagem
) {
    public static TransactionSuggestionResponseDTO suggestion(String description, BigDecimal amount, Tipo type,
                                                               String categoryLabel, UUID categoryId) {
        return new TransactionSuggestionResponseDTO(Status.SUGESTAO, description, amount, type,
                categoryLabel, categoryId, null, null);
    }

    public static TransactionSuggestionResponseDTO clarification(Motivo reason) {
        return new TransactionSuggestionResponseDTO(Status.ESCLARECIMENTO, null, null, null, null, null,
                reason, reason.message);
    }

    public enum Status {
        SUGESTAO,
        ESCLARECIMENTO
    }

    public enum Tipo {
        RECEITA(TransactionType.INCOME),
        DESPESA(TransactionType.EXPENSE);

        private final TransactionType transactionType;

        Tipo(TransactionType transactionType) {
            this.transactionType = transactionType;
        }

        public TransactionType transactionType() {
            return transactionType;
        }
    }

    public enum Motivo {
        VALOR_AUSENTE("O valor está faltando. Repita seu prompt informando também o valor."),
        MULTIPLAS_TRANSACOES("Envie um lançamento por vez para que eu possa sugerir os dados."),
        FORA_DE_CONTEXTO("Descreva uma receita ou despesa que você quer registrar."),
        DADOS_AMBIGUOS("Informe a descrição, se foi uma receita ou despesa e o valor em reais.");

        private final String message;

        Motivo(String message) {
            this.message = message;
        }
    }
}
