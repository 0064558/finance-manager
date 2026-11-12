package com.rodrigs.finance_manager_api.transaction.service;

import com.rodrigs.finance_manager_api.transaction.service.TransactionChatRagEvaluationTest.CategoryComparison;
import com.rodrigs.finance_manager_api.transaction.service.TransactionChatRagEvaluationTest.Measurement;
import com.rodrigs.finance_manager_api.transaction.service.TransactionChatRagEvaluationTest.Variant;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionChatRagAcceptanceTest {
    @Test
    void acceptsSafeFallbackAndDoesNotCountItAsACategoryCorrection() {
        verify(fixture(true, false, true, 200));
    }

    @Test
    void rejectsFallbackWhenGenerationFailsOrExtractionChanges() {
        assertThatThrownBy(() -> verify(fixture(false, false, true, 200)))
                .isInstanceOf(AssertionError.class).hasMessageContaining("RAG deve concluir");
        assertThatThrownBy(() -> verify(fixture(true, true, true, 200)))
                .isInstanceOf(AssertionError.class).hasMessageContaining("RAG deve concluir");
    }

    @Test
    void stillRequiresCorrectionsToOutnumberRegressions() {
        assertThatThrownBy(() -> verify(fixture(true, false, false, 200)))
                .isInstanceOf(AssertionError.class).hasMessageContaining("Correções de categoria");
    }

    @Test
    void stillRejectsLatencyAboveTheOriginalBudget() {
        assertThatThrownBy(() -> verify(fixture(true, false, true, 2500)))
                .isInstanceOf(AssertionError.class).hasMessageContaining("p95 do RAG");
    }

    static void verify(List<Measurement> rows) {
        var comparisons = new EnumMap<CategoryComparison, Integer>(CategoryComparison.class);
        rows.stream().filter(row -> row.variant() == Variant.CATEGORIES).forEach(catalogue -> {
            var rag = rows.stream().filter(row -> row.variant() == Variant.RAG && row.name().equals(catalogue.name()))
                    .findFirst().orElseThrow();
            comparisons.merge(TransactionChatRagEvaluationTest.compareCategories(catalogue, rag), 1, Integer::sum);
        });
        TransactionChatRagEvaluationTest.verifyAcceptance(rows, comparisons);
    }

    private List<Measurement> fixture(boolean extraction, boolean chatFailure,
                                      boolean improvement, double ragMs) {
        var rows = new ArrayList<Measurement>();
        for (int index = 0; index < 20; index++) {
            String name = "case-" + index;
            boolean catalogueCorrect = !improvement || index != 1;
            rows.add(new Measurement(Variant.CATEGORIES, name, null, catalogueCorrect, !catalogueCorrect,
                    true, false, false, 0, 100));
            rows.add(new Measurement(Variant.RAG, name, null, true, false,
                    index != 0 || extraction, index == 0 && chatFailure, index == 0, index == 0 ? 0 : 3, ragMs));
        }
        return rows;
    }
}
