package com.rodrigs.finance_manager_api.transaction.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionEmbeddingTextTest {
    @Test
    void keepsPurposeAndRemovesAmountsAndDates() {
        assertThat(TransactionEmbeddingText.normalize("  Uber   para o plantão à noite R$ 1.243,50 em 11/11/2026  "))
                .isEqualTo("Uber para o plantão à noite em");
        assertThat(TransactionEmbeddingText.normalize("Paguei 39.90 de almoço no plantão em 2026-11-11"))
                .isEqualTo("Paguei de almoço no plantão em");
        assertThat(TransactionEmbeddingText.normalize("Gastei 20 no almoço"))
                .isEqualTo("Gastei no almoço");
        assertThat(TransactionEmbeddingText.normalize("Uber para o Festival 7 de Setembro"))
                .isEqualTo("Uber para o Festival 7 de Setembro");
    }

    @Test
    void removesRecognizableContactPaymentAndAccountIdentifiers() {
        String description = "Ingresso para festival pessoa+teste@example.invalid +55 (11) 99876-4321 "
                + "CPF: 123.456.789-09 CNPJ: 12.345.678/0001-99 conta corrente nº 12345-6 agência 1234 "
                + "1234 5678 9101 1121 123e4567-e89b-12d3-a456-426614174000";

        assertThat(TransactionEmbeddingText.normalize(description)).isEqualTo("Ingresso para festival");
        assertThat(TransactionEmbeddingText.normalize("Ingresso para festival chave Pix: 123e4567-e89b-12d3-a456-426614174000"))
                .isEqualTo("Ingresso para festival");
    }

    @Test
    void treatsContentWithoutDescriptiveTextAsEmpty() {
        assertThat(TransactionEmbeddingText.normalize(null)).isEmpty();
        assertThat(TransactionEmbeddingText.normalize("R$ 12,00 11/11/2026")).isEmpty();
        assertThat(TransactionEmbeddingText.normalize("CPF: 123.456.789-09")).isEmpty();
    }
}
