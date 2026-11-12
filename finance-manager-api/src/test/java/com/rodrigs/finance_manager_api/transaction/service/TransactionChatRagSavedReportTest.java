package com.rodrigs.finance_manager_api.transaction.service;

import com.rodrigs.finance_manager_api.transaction.service.TransactionChatRagEvaluationTest.Measurement;
import com.rodrigs.finance_manager_api.transaction.service.TransactionChatRagEvaluationTest.Variant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Reassess frozen live measurements after changes to the verifier, without new provider calls. */
@EnabledIfSystemProperty(named = "transaction.chat.rag.report", matches = ".+")
class TransactionChatRagSavedReportTest {
    @Test
    void verifiesEveryFrozenMeasurementAgainstTheSameAcceptancePolicy() throws Exception {
        var root = JsonMapper.builder().build().readTree(Files.readAllBytes(Path.of(System.getProperty("transaction.chat.rag.report"))));
        assertThat(root.isArray()).isTrue();
        assertThat(root.size()).isEqualTo(60);
        var rows = new ArrayList<Measurement>();
        for (var row : root) {
            for (String field : new String[]{"Case", "Variant", "Correct", "Wrong", "Extraction", "ChatFailed",
                    "RetrievalFailed", "Examples", "Ms"}) {
                assertThat(row.has(field)).as("Required measurement field %s", field).isTrue();
            }
            rows.add(new Measurement(Variant.valueOf(row.path("Variant").asString()), row.path("Case").asString(), null,
                    row.path("Correct").asBoolean(), row.path("Wrong").asBoolean(), row.path("Extraction").asBoolean(),
                    row.path("ChatFailed").asBoolean(), row.path("RetrievalFailed").asBoolean(),
                    row.path("Examples").asInt(), row.path("Ms").asDouble()));
        }
        var cases = rows.stream().collect(Collectors.groupingBy(Measurement::name));
        assertThat(cases).hasSize(20);
        cases.values().forEach(measurements -> assertThat(measurements).extracting(Measurement::variant)
                .containsExactlyInAnyOrderElementsOf(Arrays.asList(Variant.values())));
        assertThat(rows).allSatisfy(row -> assertThat(row.ms()).isGreaterThanOrEqualTo(0));
        TransactionChatRagAcceptanceTest.verify(rows);
        System.out.println("RAG saved report: all 60 live measurements satisfy the acceptance policy.");
    }
}
