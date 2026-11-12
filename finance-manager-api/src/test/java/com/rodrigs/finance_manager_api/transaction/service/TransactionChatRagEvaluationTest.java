package com.rodrigs.finance_manager_api.transaction.service;

import com.rodrigs.finance_manager_api.shared.exception.TransactionSuggestionUnavailableException;
import com.rodrigs.finance_manager_api.transaction.dto.TransactionSuggestionResponseDTO;
import com.rodrigs.finance_manager_api.transaction.dto.TransactionSuggestionResponseDTO.Status;
import com.rodrigs.finance_manager_api.transaction.dto.TransactionSuggestionResponseDTO.Tipo;
import com.rodrigs.finance_manager_api.transaction.rag.TransactionEmbeddingClient;
import com.rodrigs.finance_manager_api.transaction.rag.TransactionEmbeddingRepository;
import com.rodrigs.finance_manager_api.transaction.rag.TransactionEmbeddingText;
import com.rodrigs.finance_manager_api.transaction.rag.TransactionRagProperties;
import com.rodrigs.finance_manager_api.transaction.service.TransactionSuggestionService.ChatMessage;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.ai.retry.max-attempts=1",
        "finance-manager.transaction-chat.rag.indexing-initial-delay=3600000"
})
@EnabledIf("experimentEnabled")
class TransactionChatRagEvaluationTest {
    private static final String LIVE_PROPERTY = "transaction.chat.rag.evaluate";
    private static final String POSTGRES_PROPERTY = "transaction.chat.rag.postgres";
    private static final long REQUEST_PAUSE_MS = 4_000;
    private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 11, 11);
    private static final UUID CORPUS_USER_ID = UUID.fromString("0e4352be-5c64-4ea4-b6c2-7eb5e327142c");
    private static final UUID EMPTY_HISTORY_USER_ID = UUID.fromString("6a9bbd55-4310-44d5-b4e6-deafec49a69b");
    private static final Duration FIXTURE_LEASE = Duration.ofMinutes(1);
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionEmbeddingRepository history;
    @Autowired
    private TransactionRagProperties ragSettings;
    @Autowired
    private MeterRegistry meters;
    @Autowired
    private ObjectProvider<TransactionSuggestionService> chat;
    @Autowired
    private ObjectProvider<TransactionEmbeddingClient> embeddings;

    @Autowired
    private ObjectProvider<org.springframework.ai.chat.model.ChatModel> chatModels;
    @Autowired
    private ObjectProvider<com.google.genai.Client> geminiClients;
    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @Test
    @EnabledIfSystemProperty(named = "transaction.chat.rag.diagnose", matches = "true")
    void diagnosesTheSpringClientWithFixedPublicInput() {
        var model = chatModels.getObject();
        var client = geminiClients.getObject();
        String key = System.getenv("GEMINI_API_KEY").strip();
        System.out.printf("Gemini Spring diagnostic: model=%s, expectedCredential=%s%n",
                model.getOptions().getModel(), key.equals(client.apiKey()));
        String configured = applicationContext.getEnvironment().getProperty("spring.ai.google.genai.api-key");
        System.out.printf("Gemini Spring diagnostic: beans=%s, propertyMatchesEnvironment=%s, clientMatchesProperty=%s%n",
                java.util.Arrays.toString(applicationContext.getBeanNamesForType(com.google.genai.Client.class)),
                key.equals(configured), java.util.Objects.equals(configured, client.apiKey()));
        assertThat(key.equals(client.apiKey())).as("O cliente deve usar a credencial fornecida no ambiente da avaliação")
                .isTrue();
        try {
            model.call("Reply with OK.");
            System.out.println("Gemini Spring diagnostic: fixed-input generation succeeded.");
        } catch (RuntimeException failure) {
            Throwable current = failure;
            for (int depth = 0; current != null && depth < 32; depth++, current = current.getCause()) {
                if (current instanceof com.google.genai.errors.ApiException api) {
                    String detail = api.message().replace(key, "[redacted]").replaceAll("[\\r\\n]", " ");
                    System.out.printf("Gemini Spring diagnostic: status=%d, detail=%s%n", api.code(),
                            detail.substring(0, Math.min(detail.length(), 500)));
                    break;
                }
            }
            throw new AssertionError("Fixed-input Spring diagnostic failed; see sanitized diagnostic.");
        }
    }

    static boolean experimentEnabled() {
        return Boolean.getBoolean(LIVE_PROPERTY) || Boolean.getBoolean(POSTGRES_PROPERTY);
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        System.out.println("RAG local: iniciando o PostgreSQL descartável.");
        POSTGRES.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.ai.model.chat", () -> Boolean.getBoolean(LIVE_PROPERTY) ? "google-genai" : "none");
        registry.add("finance-manager.transaction-chat.rag.enabled", () -> Boolean.getBoolean(LIVE_PROPERTY));
        registry.add("finance-manager.transaction-chat.rag.pilot-user-ids", () -> CORPUS_USER_ID + "," + EMPTY_HISTORY_USER_ID);
    }

    @Test
    @EnabledIfSystemProperty(named = POSTGRES_PROPERTY, matches = "true")
    void searchesOnlyOwnedCurrentTransactionsAndRemovesDeletedVectors() {
        var owner = user(UUID.randomUUID());
        var other = user(UUID.randomUUID());
        UUID food = category(owner, "Restaurantes", "EXPENSE");
        UUID work = category(owner, "Trabalho", "EXPENSE");
        UUID privateCategory = category(other, "Categoria privada", "EXPENSE");
        var query = vector(1, 0);
        UUID first = transaction(owner, food, "EXPENSE", "Jantar no sushi");
        transaction(owner, food, "EXPENSE", "Jantar no restaurante");
        transaction(owner, food, "EXPENSE", "Bicicleta");
        transaction(other, privateCategory, "EXPENSE", "Jantar privado");
        indexHistory(owner, description -> switch (description) {
            case "Jantar no sushi" -> vector(0.9f, 0.1f);
            case "Jantar no restaurante" -> vector(0.8f, 0.3f);
            default -> vector(0, 1);
        }, 3);
        indexHistory(other, description -> query, 1);

        assertThat(matches(owner, query)).extracting(TransactionEmbeddingRepository.Match::description)
                .containsExactly("Jantar no sushi", "Jantar no restaurante");

        jdbc.update("UPDATE transactions SET category_id = ? WHERE id = ?", work, first);
        assertThat(matches(owner, query).getFirst().categoryId()).isEqualTo(work);
        jdbc.update("UPDATE transactions SET description = ? WHERE id = ?", "Almoço no sushi", first);
        assertThat(matches(owner, query)).hasSize(1);
        indexHistory(owner, description -> query, 1);
        assertThat(matches(owner, query).getFirst().description()).isEqualTo("Almoço no sushi");
        jdbc.update("DELETE FROM transactions WHERE id = ?", first);
        assertThat(matches(owner, query)).extracting(TransactionEmbeddingRepository.Match::description)
                .containsExactly("Jantar no restaurante");
        assertThat(matches(user(UUID.randomUUID()), query)).isEmpty();
    }

    @Test
    @EnabledIfSystemProperty(named = LIVE_PROPERTY, matches = "true")
    void comparesCurrentChatCategoriesAndRagUsingFictitiousTransactions() throws InterruptedException {
        String evaluationKey = System.getenv("GEMINI_API_KEY");
        assertThat(evaluationKey).as("GEMINI_API_KEY deve estar configurada para a avaliação").isNotBlank();
        assertThat(evaluationKey.strip().equals(geminiClients.getObject().apiKey()))
                .as("O cliente de geração deve usar a credencial fornecida no ambiente da avaliação").isTrue();
        var owner = user(CORPUS_USER_ID);
        var emptyHistoryUser = user(EMPTY_HISTORY_USER_ID);
        UUID dining = category(owner, "Restaurantes", "EXPENSE");
        UUID market = category(owner, "Mercado", "EXPENSE");
        UUID work = category(owner, "Plantão", "EXPENSE");
        UUID personal = category(owner, "Rotina", "EXPENSE");
        UUID leisure = category(owner, "Lazer", "EXPENSE");
        UUID home = category(owner, "Casa", "EXPENSE");
        UUID study = category(owner, "Estudos", "EXPENSE");
        UUID extra = category(owner, "Receitas pontuais", "INCOME");
        UUID reimbursements = category(owner, "Acertos", "INCOME");
        UUID salary = category(owner, "Salário", "INCOME");
        UUID transport = category(emptyHistoryUser, "Transporte", "EXPENSE");
        UUID diningWithoutHistory = category(emptyHistoryUser, "Restaurantes", "EXPENSE");

        var cases = List.of(
                new Case(owner, "restaurante", "Paguei R$ 39,90 no japonês", dining, "39.90", Tipo.DESPESA),
                new Case(owner, "mercado", "Gastei R$ 27,50 comprando hortaliças na feira", market, "27.50", Tipo.DESPESA),
                new Case(owner, "trabalho", "Paguei R$ 31,50 de Uber para trabalhar à noite", work, "31.50", Tipo.DESPESA),
                new Case(owner, "rotina", "Paguei R$ 8,70 no ônibus voltando para casa", personal, "8.70", Tipo.DESPESA),
                new Case(owner, "receita", "Recebi R$ 230,00 de um trabalho avulso traduzindo", extra, "230.00", Tipo.RECEITA),
                new Case(emptyHistoryUser, "sem-historico", "Gastei R$ 22,00 no táxi", transport, "22.00", Tipo.DESPESA),
                new Case(owner, "pao-e-leite", "Comprei pão e leite por R$ 16,80 no supermercado", market, "16.80", Tipo.DESPESA),
                new Case(owner, "pizzaria", "Paguei R$ 54,00 no rodízio da pizzaria", dining, "54.00", Tipo.DESPESA),
                new Case(owner, "plantao-onibus", "Gastei R$ 9,40 de ônibus para ir ao plantão da madrugada", work, "9.40", Tipo.DESPESA),
                new Case(owner, "rotina-aplicativo", "Paguei R$ 18,60 de Uber voltando para casa depois da feira", personal, "18.60", Tipo.DESPESA),
                new Case(owner, "uber-show", "Paguei R$ 28,40 de Uber para ir ao festival de música", leisure, "28.40", Tipo.DESPESA),
                new Case(owner, "ingresso-show", "Paguei R$ 85,00 no ingresso para o show", leisure, "85.00", Tipo.DESPESA),
                new Case(owner, "reparo-casa", "Paguei R$ 120,00 para desentupir a pia de casa", home, "120.00", Tipo.DESPESA),
                new Case(owner, "material-casa", "Gastei R$ 45,90 numa resistência nova para o chuveiro", home, "45.90", Tipo.DESPESA),
                new Case(owner, "reembolso-plantao", "A empresa me devolveu R$ 31,50 da corrida para o turno da noite",
                        reimbursements, "31.50", Tipo.RECEITA),
                new Case(owner, "freela-foto", "Recebi R$ 380,00 de um freela de fotografia", extra, "380.00", Tipo.RECEITA),
                new Case(owner, "salario", "Recebi R$ 2.540,00 do salário do mês", salary, "2540.00", Tipo.RECEITA),
                new Case(emptyHistoryUser, "sem-historico-restaurante", "Gastei R$ 32,00 no almoço do restaurante",
                        diningWithoutHistory, "32.00", Tipo.DESPESA),
                new Case(emptyHistoryUser, "sem-categoria", "Paguei R$ 150,00 na consulta com a dentista",
                        null, "150.00", Tipo.DESPESA),
                new Case(owner, "curso-sem-exemplo", "Paguei R$ 49,90 num curso online de Java", study, "49.90", Tipo.DESPESA));
        String selectedCase = System.getProperty("transaction.chat.rag.case");
        if (selectedCase != null) {
            cases = cases.stream().filter(example -> example.name().equals(selectedCase)).toList();
            assertThat(cases).as("Caso configurado para avaliação").hasSize(1);
        }

        var corpus = List.of(
                new SavedExample(dining, "EXPENSE", "Jantar no sushi"),
                new SavedExample(market, "EXPENSE", "Feira de frutas e verduras"),
                new SavedExample(work, "EXPENSE", "Corrida de aplicativo para o turno da madrugada"),
                new SavedExample(personal, "EXPENSE", "Passagem de ônibus para casa"),
                new SavedExample(extra, "INCOME", "Pagamento por um freela de tradução"),
                new SavedExample(leisure, "EXPENSE", "Estacionamento para o festival de música"),
                new SavedExample(leisure, "EXPENSE", "Ingresso para apresentação de uma banda"),
                new SavedExample(home, "EXPENSE", "Troca da torneira da cozinha"),
                new SavedExample(reimbursements, "INCOME", "Reembolso da empresa pelo transporte no plantão"),
                new SavedExample(extra, "INCOME", "Cachê por um freela de edição de vídeo"),
                new SavedExample(salary, "INCOME", "Pagamento mensal do salário"));
        for (var example : corpus) {
            transaction(owner, example.categoryId(), example.type(), example.description());
        }
        System.out.printf("RAG local: gerando embeddings para %d transações fictícias confirmadas.%n", corpus.size());
        indexHistory(owner, embeddings.getObject()::embedDocument, corpus.size());

        var results = new ArrayList<Measurement>();
        var comparisons = new EnumMap<CategoryComparison, Integer>(CategoryComparison.class);
        for (int index = 0; index < cases.size(); index++) {
            var variants = new ArrayList<>(List.of(Variant.values()));
            Collections.rotate(variants, index % variants.size());
            var caseResults = new EnumMap<Variant, Measurement>(Variant.class);
            for (var variant : variants) {
                if (!results.isEmpty()) Thread.sleep(REQUEST_PAUSE_MS);
                var result = measure(variant, cases.get(index));
                results.add(result);
                caseResults.put(variant, result);
                printMeasurement(result);
            }
            var categoriesResult = caseResults.get(Variant.CATEGORIES);
            var ragResult = caseResults.get(Variant.RAG);
            var comparison = compareCategories(categoriesResult, ragResult);
            comparisons.merge(comparison, 1, Integer::sum);
            System.out.printf("%s/COMPARACAO: %s, CATEGORIES=%s, RAG=%s, exemplos=%d%n", ragResult.name(), comparison,
                    Objects.toString(categoriesResult.categoryLabel(), "sem categoria"),
                    Objects.toString(ragResult.categoryLabel(), "sem categoria"), ragResult.examples());
        }
        summarize(results);
        System.out.printf("COMPARACAO: corrigiu=%d, piorou=%d, manteveAcerto=%d, manteveErro=%d, inconclusivo=%d%n",
                comparisons.getOrDefault(CategoryComparison.CORRIGIU, 0),
                comparisons.getOrDefault(CategoryComparison.PIOROU, 0),
                comparisons.getOrDefault(CategoryComparison.MANTEVE_ACERTO, 0),
                comparisons.getOrDefault(CategoryComparison.MANTEVE_ERRO, 0),
                comparisons.getOrDefault(CategoryComparison.INCONCLUSIVO, 0));
        for (var variant : Variant.values()) {
            assertThat(results).as("Respostas do chat em %s", variant)
                    .anyMatch(result -> result.variant() == variant && !result.chatFailed());
        }
        if (selectedCase == null) {
            assertThat(results).as("Ao menos uma recuperação real por embeddings deve chegar ao Gemini")
                    .anyMatch(result -> result.variant() == Variant.RAG && result.examples() > 0 && !result.chatFailed());
        }
        if (Boolean.getBoolean("transaction.chat.rag.acceptance")) {
            assertThat(selectedCase).as("Aceite exige os vinte casos, sem seleção parcial").isNull();
            verifyAcceptance(results, comparisons);
        }
    }

    static void verifyAcceptance(List<Measurement> results, EnumMap<CategoryComparison, Integer> comparisons) {
        var catalogue = results.stream().filter(result -> result.variant() == Variant.CATEGORIES).toList();
        var rag = results.stream().filter(result -> result.variant() == Variant.RAG).toList();
        var checks = new org.assertj.core.api.SoftAssertions();
        checks.assertThat(catalogue).as("Catálogo deve concluir os vinte casos para comparação pareada")
                .hasSize(20).allMatch(result -> !result.chatFailed());
        checks.assertThat(rag).as("RAG deve concluir os vinte casos com valor e tipo corretos, inclusive em fallback de busca")
                .hasSize(20).allMatch(result -> !result.chatFailed() && result.extractionCorrect());
        checks.assertThat(comparisons.getOrDefault(CategoryComparison.CORRIGIU, 0))
                .as("Correções de categoria devem superar regressões")
                .isGreaterThan(comparisons.getOrDefault(CategoryComparison.PIOROU, 0));
        checks.assertThat(p95(rag) - p95(catalogue))
                .as("p95 do RAG, incluindo falhas, deve ficar até 2 s acima do catálogo")
                .isLessThanOrEqualTo(2_000);
        checks.assertAll();
    }

    private static double p95(List<Measurement> rows) {
        var times = rows.stream().map(Measurement::ms).sorted().toList();
        return times.isEmpty() ? Double.NaN : times.get((int) Math.ceil(times.size() * 0.95) - 1);
    }

    private Measurement measure(Variant variant, Case example) {
        var retrievalBefore = retrievalMetrics();
        long start = System.nanoTime();
        var service = chat.getObject();
        var messages = List.of(new ChatMessage(example.prompt(), REFERENCE_DATE));
        List<TransactionSuggestionResponseDTO> drafts;
        boolean chatFailed = false;
        System.out.printf("RAG local: %s/%s executando o fluxo do chat.%n", example.name(), variant);
        try {
            drafts = switch (variant) {
                case CURRENT -> service.suggestChat(example.user().id(), messages, ChatCategoryContext.NONE);
                case CATEGORIES -> service.suggestChatWithCatalogue(example.user().id(), messages);
                case RAG -> service.suggestChat(example.user().id(), messages);
            };
        } catch (TransactionSuggestionUnavailableException exception) {
            drafts = List.of();
            chatFailed = true;
        }
        double elapsed = (System.nanoTime() - start) / 1_000_000.0;
        var retrievalAfter = retrievalMetrics();
        boolean retrievalFailed = retrievalAfter.failures() > retrievalBefore.failures();
        int examples = (int) (retrievalAfter.examples() - retrievalBefore.examples());
        var draft = drafts.size() == 1 ? drafts.getFirst() : null;
        UUID selected = draft == null ? null : draft.categoriaId();
        boolean extractionCorrect = draft != null && draft.tipo() == example.type() && draft.valor() != null
                && draft.valor().compareTo(new BigDecimal(example.amount())) == 0;
        boolean categoryCorrect = draft != null && draft.status() == Status.SUGESTAO
                && Objects.equals(example.categoryId(), selected);
        return new Measurement(variant, example.name(), draft == null ? null : draft.categoriaSugerida(), categoryCorrect,
                selected != null && !categoryCorrect, extractionCorrect, chatFailed, retrievalFailed, examples, elapsed);
    }

    private RetrievalMetrics retrievalMetrics() {
        long failures = List.of("timeout", "failed", "busy", "interrupted").stream()
                .map(outcome -> meters.find("finance.manager.chat.rag.search").tag("outcome", outcome).timer())
                .filter(Objects::nonNull).mapToLong(Timer::count).sum();
        double examples = meters.find("finance.manager.chat.rag.examples").summaries().stream()
                .mapToDouble(DistributionSummary::totalAmount).sum();
        return new RetrievalMetrics(failures, examples);
    }

    static CategoryComparison compareCategories(Measurement categoriesResult, Measurement ragResult) {
        if (categoriesResult.chatFailed() || ragResult.chatFailed() || ragResult.retrievalFailed()) {
            return CategoryComparison.INCONCLUSIVO;
        }
        if (categoriesResult.categoryCorrect()) {
            return ragResult.categoryCorrect() ? CategoryComparison.MANTEVE_ACERTO : CategoryComparison.PIOROU;
        }
        return ragResult.categoryCorrect() ? CategoryComparison.CORRIGIU : CategoryComparison.MANTEVE_ERRO;
    }

    private void printMeasurement(Measurement result) {
        System.out.printf("%s/%s: categoria=%s, categoriaErrada=%s, valorETipo=%s, falhaChat=%s, falhaBusca=%s, exemplos=%d, %.0f ms%n",
                result.name(), result.variant(), result.categoryCorrect(), result.wrongCategory(), result.extractionCorrect(),
                result.chatFailed(), result.retrievalFailed(), result.examples(), result.ms());
    }

    private void summarize(List<Measurement> results) {
        for (var variant : Variant.values()) {
            var rows = results.stream().filter(result -> result.variant() == variant).toList();
            var completed = rows.stream().filter(result -> !result.chatFailed()).toList();
            var times = completed.stream().map(Measurement::ms).sorted().toList();
            String median = times.isEmpty() ? "n/d"
                    : "%.0f ms".formatted((times.get((times.size() - 1) / 2) + times.get(times.size() / 2)) / 2);
            String p95 = times.isEmpty() ? "n/d" : "%.0f ms".formatted(times.get((int) Math.ceil(times.size() * 0.95) - 1));
            System.out.printf("%s: categorias certas=%d/%d, erradas=%d, valor e tipo certos=%d, falhas do chat=%d, falhas de busca=%d, mediana=%s, p95=%s%n",
                    variant, completed.stream().filter(Measurement::categoryCorrect).count(), completed.size(),
                    completed.stream().filter(Measurement::wrongCategory).count(),
                    completed.stream().filter(Measurement::extractionCorrect).count(), rows.size() - completed.size(),
                    rows.stream().filter(Measurement::retrievalFailed).count(), median, p95);
        }
    }

    private User user(UUID id) {
        jdbc.update("INSERT INTO users (id, name, email, password_hash) VALUES (?, 'Teste', ?, 'fixture')",
                id, id + "@example.test");
        UUID account = jdbc.queryForObject("""
                INSERT INTO financial_accounts (user_id, name, type, initial_balance)
                VALUES (?, 'Conta fictícia', 'CHECKING', 0) RETURNING id
                """, UUID.class, id);
        return new User(id, account);
    }

    private UUID category(User user, String name, String type) {
        return jdbc.queryForObject("""
                INSERT INTO categories (user_id, name, transaction_type) VALUES (?, ?, ?) RETURNING id
                """, UUID.class, user.id(), name, type);
    }

    private UUID transaction(User user, UUID category, String type, String description) {
        return jdbc.queryForObject("""
                INSERT INTO transactions (user_id, account_id, category_id, type, amount, occurred_on, description)
                VALUES (?, ?, ?, ?, 1, ?, ?) RETURNING id
                """, UUID.class, user.id(), user.accountId(), category, type, REFERENCE_DATE.minusDays(30), description);
    }

    private void indexHistory(User user, Function<String, float[]> embed, int expected) {
        String version = ragSettings.version();
        history.reconcile(user.id(), version, ragSettings.backfillLimit(), ragSettings.batchSize());
        for (int index = 0; index < expected; index++) {
            var work = history.claim(user.id(), version, FIXTURE_LEASE).orElseThrow();
            String text = TransactionEmbeddingText.normalize(work.description());
            assertThat(history.complete(work, embed.apply(text))).isTrue();
        }
        assertThat(history.claim(user.id(), version, FIXTURE_LEASE)).isEmpty();
    }

    private List<TransactionEmbeddingRepository.Match> matches(User user, float[] vector) {
        return history.search(user.id(), ragSettings.version(), vector, ragSettings.minSimilarity(),
                ragSettings.maxExamples(), ragSettings.searchTimeout());
    }

    private float[] vector(float first, float second) {
        float[] vector = new float[768];
        vector[0] = first;
        vector[1] = second;
        return vector;
    }

    enum Variant { CURRENT, CATEGORIES, RAG }
    enum CategoryComparison { CORRIGIU, PIOROU, MANTEVE_ACERTO, MANTEVE_ERRO, INCONCLUSIVO }
    private record User(UUID id, UUID accountId) {}
    private record SavedExample(UUID categoryId, String type, String description) {}
    private record Case(User user, String name, String prompt, UUID categoryId, String amount, Tipo type) {}
    private record RetrievalMetrics(long failures, double examples) {}
    record Measurement(Variant variant, String name, String categoryLabel, boolean categoryCorrect, boolean wrongCategory,
                               boolean extractionCorrect, boolean chatFailed, boolean retrievalFailed,
                               int examples, double ms) {}
}
