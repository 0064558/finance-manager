package com.rodrigs.finance_manager_api.support;

import com.rodrigs.finance_manager_api.FinanceManagerApiApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Full HTTP requests: real server, security, services, transactions, Flyway and PostgreSQL. */
@ActiveProfiles("test")
@SpringBootTest(classes = FinanceManagerApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.address=127.0.0.1")
public abstract class HttpRequestSpec extends PostgresIntegrationTest {
    protected static final String ACCOUNTS = "/api/v1/financial-accounts";
    protected static final String CATEGORIES = "/api/v1/categories";
    protected static final String TRANSACTIONS = "/api/v1/transactions";
    protected static final String AUTH = "/api/v1/auth";
    protected static final String REPORTS = "/api/v1/reports";
    protected static final String PASSWORD = "senha1234";
    protected static final ObjectMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    @LocalServerPort
    private int port;

    protected Reply request(String method, String path, String token, Object payload) throws Exception {
        return rawRequest(method, path, token, payload == null ? null : JSON.writeValueAsString(payload));
    }

    protected Reply rawRequest(String method, String path, String token, String body) throws Exception {
        return rawRequest(method, path, token, body, Map.of());
    }

    protected Reply requestWithKey(String method, String path, String token, Object payload, String key) throws Exception {
        return rawRequest(method, path, token, JSON.writeValueAsString(payload), Map.of("Idempotency-Key", key));
    }

    protected Reply rawRequest(String method, String path, String token, String body,
                               Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(20)).header("Accept", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        headers.forEach(builder::header);
        var response = CLIENT.send(builder.method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(), response.body());
    }

    protected UserSession user() throws Exception {
        return user("spec-" + UUID.randomUUID() + "@example.test");
    }

    protected UserSession user(String email) throws Exception {
        Reply registered = request("POST", AUTH + "/register", null, registration("Pessoa de teste", email, PASSWORD));
        registered.status(201);
        Reply login = request("POST", AUTH + "/login", null, credentials(email, PASSWORD));
        login.status(200);
        return new UserSession(registered.text("id"), email, login.text("accessToken"));
    }

    protected String account(UserSession user, String name, String balance) throws Exception {
        Reply reply = request("POST", ACCOUNTS, user.token(), accountBody(name, "CHECKING", balance));
        reply.status(201);
        return reply.text("id");
    }

    protected String category(UserSession user, String name, String type) throws Exception {
        Reply reply = request("POST", CATEGORIES, user.token(), categoryBody(name, type));
        reply.status(201);
        return reply.text("id");
    }

    protected String transaction(UserSession user, String account, String category, String type, String amount,
                                 LocalDate date) throws Exception {
        Reply reply = request("POST", TRANSACTIONS, user.token(), transactionBody(account, category, type, amount, date));
        reply.status(201);
        return reply.text("id");
    }

    protected Map<String, Object> registration(String name, String email, String password) {
        return Map.of("name", name, "email", email, "password", password);
    }

    protected Map<String, Object> credentials(String email, String password) {
        return Map.of("email", email, "password", password);
    }

    protected Map<String, Object> accountBody(String name, String type, String balance) {
        return Map.of("name", name, "type", type, "initialBalance", new BigDecimal(balance));
    }

    protected Map<String, Object> categoryBody(String name, String type) {
        return Map.of("name", name, "transactionType", type);
    }

    protected Map<String, Object> transactionBody(String account, String category, String type, String amount,
                                                 LocalDate date) {
        return Map.of("accountId", account, "categoryId", category, "type", type,
                "amount", new BigDecimal(amount), "occurredOn", date.toString());
    }

    protected String period(LocalDate start, LocalDate end) {
        return "?startDate=" + start + "&endDate=" + end;
    }

    protected Reply balances(UserSession user) throws Exception {
        Reply result = request("GET", REPORTS + "/balances", user.token(), null);
        result.status(200);
        return result;
    }

    /** All workers are ready before releasing any request; no mocks, sleeps or serial futures. */
    @SafeVarargs
    protected final List<Reply> simultaneously(Callable<Reply>... calls) throws Exception {
        CountDownLatch ready = new CountDownLatch(calls.length);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<java.util.concurrent.Future<Reply>>();
            for (var call : calls) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Concurrent start timed out");
                    return call.call();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).as("all HTTP workers ready").isTrue();
            start.countDown();
            List<Reply> results = new ArrayList<>();
            for (var future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        } finally {
            start.countDown();
        }
    }

    protected record UserSession(String id, String email, String token) {}

    protected record Reply(int code, String body) {
        public void status(int expected) {
            assertThat(code).as("HTTP status; response: %s", body).isEqualTo(expected);
        }

        public Object value(String path) {
            Object current = JSON.readValue(body, Object.class);
            if (path.isEmpty()) return current;
            for (String segment : path.split("\\.")) {
                current = current instanceof List<?> list ? list.get(Integer.parseInt(segment))
                        : ((Map<?, ?>) current).get(segment);
            }
            return current;
        }

        public String text(String path) { return (String) value(path); }
        public List<?> list(String path) { return (List<?>) value(path); }
        public void money(String path, String expected) {
            assertThat(new BigDecimal(value(path).toString())).as(path).isEqualByComparingTo(expected);
        }
    }
}
