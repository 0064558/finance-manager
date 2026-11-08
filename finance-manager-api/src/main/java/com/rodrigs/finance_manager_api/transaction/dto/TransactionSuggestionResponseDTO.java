package com.rodrigs.finance_manager_api.transaction.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rodrigs.finance_manager_api.shared.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
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
        String mensagem,
        LocalDate data
) {
    public static TransactionSuggestionResponseDTO suggestion(String description, BigDecimal amount, Tipo type,
                                                               String categoryLabel, UUID categoryId) {
        return suggestion(description, amount, type, categoryLabel, categoryId, null);
    }

    public static TransactionSuggestionResponseDTO suggestion(String description, BigDecimal amount, Tipo type,
                                                               String categoryLabel, UUID categoryId, LocalDate date) {
        return new TransactionSuggestionResponseDTO(Status.SUGESTAO, description, amount, type,
                categoryLabel, categoryId, null, null, date);
    }

    public static TransactionSuggestionResponseDTO clarification(Motivo reason) {
        return clarification(reason, reason.message);
    }

    public static TransactionSuggestionResponseDTO chatClarification(Motivo reason) {
        String message = switch (reason) {
            case VALOR_AUSENTE -> "Qual foi o valor em reais?";
            case DADOS_AMBIGUOS -> "Pode esclarecer a descrição, o tipo (receita ou despesa) ou o valor em reais?";
            default -> reason.message;
        };
        return clarification(reason, message);
    }

    private static TransactionSuggestionResponseDTO clarification(Motivo reason, String message) {
        return new TransactionSuggestionResponseDTO(Status.ESCLARECIMENTO, null, null, null, null, null,
                reason, message, null);
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
        DADOS_AMBIGUOS("Informe a descrição, se foi uma receita ou despesa e o valor em reais."),
        DATA_AMBIGUA("Qual foi a data do lançamento? Informe uma data válida, sem indicar uma data futura."),
        LOTE_INCOMPLETO("Confira os dados de cada lançamento e envie a lista completa com os valores em reais."),
        LIMITE_DE_LANCAMENTOS("Envie até cinco lançamentos por mensagem.");

        private final String message;

        Motivo(String message) {
            this.message = message;
        }
    }
}
