package com.rodrigs.finance_manager_api.transaction.evaluation;

import com.rodrigs.finance_manager_api.support.HttpRequestSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.rodrigs.finance_manager_api.transaction.evaluation.TransactionSuggestionEvaluationCases.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in live HTTP evaluation against Gemini with disposable PostgreSQL and fictitious data. */
@EnabledIfSystemProperty(named = "transaction.suggestions.evaluate", matches = "true")
@TestPropertySource(properties = {
        "spring.ai.model.chat=google-genai",
        "spring.ai.retry.max-attempts=1"
})
class TransactionSuggestionEvaluationTest extends HttpRequestSpec {
    @Test
    void evaluateLiveGemini() throws Exception {
        assertThat(EXAMPLES).hasSize(18);
        var examples = selectedExamples();
        var user = user();
        var categoryIds = new HashMap<String, String>();
        for (var entry : EXPENSE_CATEGORIES.entrySet()) {
            categoryIds.put(entry.getKey(), category(user, entry.getValue(), "EXPENSE"));
        }
        for (var entry : INCOME_CATEGORIES.entrySet()) {
            categoryIds.put(entry.getKey(), category(user, entry.getValue(), "INCOME"));
        }

        var results = new ArrayList<Measurement>();
        for (var example : examples) {
            // Pace the calls to avoid bursts against the provider's quota.
            if (!results.isEmpty()) {
                Thread.sleep(4_000);
            }
            results.add(measure(user, categoryIds, example));
        }
        print("Gemini (execução real via API local)", results);
        assertThat(request("GET", TRANSACTIONS, user.token(), null).list("content")).isEmpty();
    }

    private List<Example> selectedExamples() {
        String configuredIds = System.getProperty("transaction.suggestions.cases");
        if (configuredIds == null) {
            return EXAMPLES;
        }
        var ids = Set.of(configuredIds.split(","));
        var examples = EXAMPLES.stream().filter(example -> ids.contains(example.id())).toList();
        assertThat(examples).as("IDs configurados para reavaliação").hasSize(ids.size());
        return examples;
    }

    private Measurement measure(UserSession user, Map<String, String> categoryIds, Example example) throws Exception {
        long start = System.nanoTime();
        Reply reply;
        try {
            reply = request("POST", TRANSACTIONS + "/suggestions", user.token(),
                    Map.of("description", example.prompt()), Duration.ofSeconds(60));
        } catch (IOException exception) {
            return new Measurement(example, failure(exception.getClass().getSimpleName()), elapsedMs(start), true, false);
        }
        double elapsed = elapsedMs(start);
        if (reply.code() != 200) {
            boolean blocked = reply.code() == 429 || reply.code() == 503 || reply.code() == 504;
            return new Measurement(example, failure("HTTP " + reply.code()), elapsed, blocked, false);
        }
        var result = new Result(reply.text("status"), reply.text("descricao"), readAmount(reply),
                reply.text("tipo"), reply.text("categoriaSugerida"), reply.text("motivo"));
        String expectedCategoryId = categoryIds.get(example.expected().category());
        boolean wrongCategoryId = !Objects.equals(expectedCategoryId, reply.text("categoriaId"));
        return new Measurement(example, result, elapsed, false, wrongCategoryId);
    }

    private static BigDecimal readAmount(Reply reply) {
        return reply.value("valor") instanceof Number value ? new BigDecimal(value.toString()) : null;
    }

    private static Result failure(String status) {
        return new Result(status, null, null, null, null, null);
    }
}
