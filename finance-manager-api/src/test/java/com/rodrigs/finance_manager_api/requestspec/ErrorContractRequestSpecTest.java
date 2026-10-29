package com.rodrigs.finance_manager_api.requestspec;

import com.rodrigs.finance_manager_api.support.HttpRequestSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("describe Contrato público dos erros por HTTP")
class ErrorContractRequestSpecTest extends HttpRequestSpec {
    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/financial-accounts", "/api/v1/categories", "/api/v1/transactions"})
    @DisplayName("it quando o recurso inexiste ou o UUID é inválido, então retorna 404 ou 400 com erro público seguro")
    void missingResourceAndMalformedId(String route) throws Exception {
        var user = user();
        var missing = request("GET", route + "/" + UUID.randomUUID(), user.token(), null);
        missing.status(404);
        safeProblem(missing, 404);
        var invalid = request("GET", route + "/invalido", user.token(), null);
        invalid.status(400);
        safeProblem(invalid, 400);
    }

    @Test
    @DisplayName("it quando JSON é malformado ou campos são inválidos, então não persiste dados e retorna ProblemDetail sem detalhes internos")
    void malformedBodies() throws Exception {
        var user = user();
        for (String route : List.of(ACCOUNTS, CATEGORIES, TRANSACTIONS, AUTH + "/register", AUTH + "/login")) {
            for (String body : List.of("{invalido", "{}")) {
                var reply = rawRequest("POST", route, user.token(), body);
                reply.status(400);
                safeProblem(reply, 400);
            }
        }
        assertThat(request("GET", ACCOUNTS, user.token(), null).list("")).isEmpty();
        assertThat(request("GET", CATEGORIES, user.token(), null).list("")).isEmpty();
        assertThat(request("GET", TRANSACTIONS, user.token(), null).list("content")).isEmpty();
        balances(user).money("totalBalance", "0");
    }

    private void safeProblem(Reply reply, int status) {
        assertThat(reply.value("status")).isEqualTo(status);
        for (String field : List.of("title", "detail", "code", "traceId", "timestamp", "instance")) {
            assertThat(reply.text(field)).as(field).isNotBlank();
        }
        assertThat(reply.body()).doesNotContain("password_hash", "passwordHash", "org.postgresql",
                "java.lang", "org.hibernate", "stackTrace", "SELECT ", PASSWORD);
        assertNoCredentials(reply.value(""));
    }

    private void assertNoCredentials(Object value) {
        if (value instanceof Map<?, ?> object) {
            // {"field":"password"} identifies an invalid field; {"password":"..."} leaks credentials.
            for (String key : List.of("password", "passwordHash", "password_hash")) {
                assertThat(object.containsKey(key)).as("credential property %s", key).isFalse();
            }
            object.values().forEach(this::assertNoCredentials);
        } else if (value instanceof List<?> list) {
            list.forEach(this::assertNoCredentials);
        } else if (value instanceof String text) {
            assertThat(text).doesNotContain(PASSWORD)
                    .doesNotContainPattern("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");
        }
    }
}
