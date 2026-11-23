package com.rodrigs.finance_manager_api.transaction.service;

import com.rodrigs.finance_manager_api.category.dto.CategoryResponseDTO;
import com.rodrigs.finance_manager_api.category.service.CategoryService;
import com.rodrigs.finance_manager_api.transaction.dto.TransactionSuggestionResponseDTO.Status;
import com.rodrigs.finance_manager_api.transaction.service.TransactionChatRagEvaluationTest.Case;
import com.rodrigs.finance_manager_api.transaction.service.TransactionSuggestionService.ChatMessage;
import com.rodrigs.finance_manager_api.transaction.service.TransactionSuggestionService.ModelChatDraft;
import com.rodrigs.finance_manager_api.transaction.service.TransactionSuggestionService.ModelChatResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class TransactionChatLocalRagReplay {
    private static final Path HISTORICAL_REPORT = Path.of("../docs/avaliacoes/rag-transporte-2026-11-12.json");
    private static final Path OUTPUT = Path.of("target/rag-local-replay.json");
    private static final int WARMUP_ROUNDS = 2;
    private static final int MEASURED_ROUNDS = 10;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SEARCH_METRIC = "finance.manager.chat.rag.search";

    private final MeterRegistry meters;
    private ChatResponse replayedResponse;

    TransactionChatLocalRagReplay(MeterRegistry meters) {
        this.meters = meters;
    }

    ChatModel model() {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(GoogleGenAiChatOptions.builder().build());
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> replayedResponse);
        return model;
    }

    void run(List<Case> cases, LocalDate referenceDate, TransactionSuggestionService service,
             CategoryService categoryService) throws IOException {
        var historical = historicalCatalogue();
        assertThat(historical.keySet()).containsExactlyInAnyOrderElementsOf(cases.stream().map(Case::name).toList());
        var report = new EnumMap<Scenario, Map<String, CaseResult>>(Scenario.class);
        for (var scenario : Scenario.values()) {
            var results = new LinkedHashMap<String, CaseResult>();
            report.put(scenario, results);
            for (int round = -WARMUP_ROUNDS; round < MEASURED_ROUNDS; round++) {
                for (int index = 0; index < cases.size(); index++) {
                    var example = cases.get(index);
                    var catalogue = categoryService.findAllCategories(example.user().id());
                    UUID replayedId = scenario == Scenario.CATALOGO_DE_12_11
                            ? replayCategory(example, historical.get(example.name()), catalogue) : null;
                    replayedResponse = response(example, replayedId);
                    var messages = List.of(new ChatMessage(example.prompt(), referenceDate));
                    boolean localFirst = (round + index) % 2 != 0;
                    var first = measure(service, example, messages, localFirst);
                    var second = measure(service, example, messages, !localFirst);
                    if (round >= 0) {
                        var result = results.computeIfAbsent(example.name(), name -> new CaseResult(
                                categoryName(catalogue, example.categoryId()), new Measurements(), new Measurements()));
                        result.catalogue().add(localFirst ? second : first, catalogue);
                        result.localRag().add(localFirst ? first : second, catalogue);
                    }
                }
            }
        }
        Files.createDirectories(OUTPUT.getParent());
        JSON.writerWithDefaultPrettyPrinter().writeValue(OUTPUT.toFile(), report);
        System.out.println("Medições da reprodução local em " + OUTPUT);
    }

    private Map<String, JsonNode> historicalCatalogue() {
        var catalogue = new LinkedHashMap<String, JsonNode>();
        for (var row : JSON.readTree(HISTORICAL_REPORT.toFile())) {
            if ("CATEGORIES".equals(row.path("Variant").asString())) {
                assertThat(row.path("Extraction").asBoolean()).isTrue();
                assertThat(row.path("ChatFailed").asBoolean()).isFalse();
                assertThat(catalogue.put(row.path("Case").asString(), row)).isNull();
            }
        }
        return catalogue;
    }

    private UUID replayCategory(Case example, JsonNode row, List<CategoryResponseDTO> catalogue) {
        if (row.path("Correct").asBoolean()) {
            return example.categoryId();
        }
        if (!row.path("Wrong").asBoolean()) {
            return null;
        }
        // The transport log's /COMPARACAO lines identify these two wrong catalogue choices as ROTINA.
        assertThat(example.name()).isIn("trabalho", "plantao-onibus");
        return catalogue.stream().filter(category -> category.name().equals("Rotina"))
                .findFirst().orElseThrow().id();
    }

    private ChatResponse response(Case example, UUID replayedId) {
        var draft = new ModelChatDraft(null, "Lançamento fictício", new BigDecimal(example.amount()),
                example.type(), Objects.toString(replayedId, null));
        var body = new ModelChatResponse(Status.SUGESTAO, null, List.of(draft));
        return new ChatResponse(List.of(new Generation(new AssistantMessage(JSON.writeValueAsString(body)))));
    }

    private Sample measure(TransactionSuggestionService service, Case example, List<ChatMessage> messages,
                           boolean local) {
        var before = searchCounts();
        long start = System.nanoTime();
        var drafts = local ? service.suggestChat(example.user().id(), messages)
                : service.suggestChatWithCatalogue(example.user().id(), messages);
        long micros = (System.nanoTime() - start) / 1_000;

        assertThat(drafts).as(example.name()).hasSize(1);
        var draft = drafts.getFirst();
        assertThat(draft.status()).as(example.name()).isEqualTo(Status.SUGESTAO);
        assertThat(draft.tipo()).as(example.name()).isEqualTo(example.type());
        assertThat(draft.valor()).as(example.name()).isEqualByComparingTo(example.amount());
        var outcomes = searchCounts().entrySet().stream()
                .filter(entry -> entry.getValue() > before.getOrDefault(entry.getKey(), 0L))
                .map(Map.Entry::getKey).toList();
        assertThat(outcomes).hasSizeLessThanOrEqualTo(local ? 1 : 0);
        String search = outcomes.isEmpty() ? "not_called" : outcomes.getFirst();
        return new Sample(draft.categoriaId(), search, micros);
    }

    private Map<String, Long> searchCounts() {
        return meters.find(SEARCH_METRIC).timers().stream()
                .collect(Collectors.toMap(timer -> timer.getId().getTag("outcome"), Timer::count));
    }

    private static String categoryName(List<CategoryResponseDTO> catalogue, UUID id) {
        if (id == null) {
            return null;
        }
        return catalogue.stream().filter(category -> category.id().equals(id)).findFirst().orElseThrow().name();
    }

    private enum Scenario { CATALOGO_DE_12_11, CATEGORIA_NULA }

    private record Sample(UUID category, String search, long micros) {}

    record CaseResult(String expected, Measurements catalogue, Measurements localRag) {}

    record Outcome(String category, String search) {}

    record Measurements(List<Outcome> outcomes, List<Long> serviceMicros) {
        Measurements() {
            this(new ArrayList<>(), new ArrayList<>());
        }

        void add(Sample sample, List<CategoryResponseDTO> catalogue) {
            outcomes.add(new Outcome(categoryName(catalogue, sample.category()), sample.search()));
            serviceMicros.add(sample.micros());
        }
    }
}
