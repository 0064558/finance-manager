package com.rodrigs.finance_manager_api.requestspec;

import com.rodrigs.finance_manager_api.support.HttpRequestSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("describe Receitas e despesas por HTTP")
class TransactionsRequestSpecTest extends HttpRequestSpec {
    @Test
    @DisplayName("it quando cria, edita, move e exclui lançamentos, então saldos e relatórios refletem cada operação uma única vez")
    void financialLifecycle() throws Exception {
        var user = user();
        var first = account(user, "Primeira", "100.00");
        var second = account(user, "Segunda", "200.00");
        var income = category(user, "Receita", "INCOME");
        var expense = category(user, "Despesa", "EXPENSE");
        transaction(user, first, income, "INCOME", "50.05", LocalDate.now());
        var id = transaction(user, first, expense, "EXPENSE", "30.10", LocalDate.now());
        request("GET", ACCOUNTS + "/" + first, user.token(), null).money("initialBalance", "100");
        balances(user).money("totalBalance", "319.95");
        var changed = new HashMap<>(transactionBody(second, expense, "EXPENSE", "40.20", LocalDate.now().minusDays(1)));
        changed.put("description", "  Compra atualizada  ");
        request("PUT", TRANSACTIONS + "/" + id, user.token(), changed).status(200);
        var found = request("GET", TRANSACTIONS + "/" + id, user.token(), null);
        assertThat(found.text("description")).isEqualTo("Compra atualizada");
        assertThat(found.text("accountId")).isEqualTo(second);
        found.money("amount", "40.20");
        var balances = balances(user);
        balances.money("accounts.0.balance", "150.05");
        balances.money("accounts.1.balance", "159.80");
        balances.money("totalBalance", "309.85");
        var summary = request("GET", REPORTS + "/summary" + period(LocalDate.now().minusDays(1), LocalDate.now()), user.token(), null);
        summary.status(200);
        summary.money("totalIncome", "50.05");
        summary.money("totalExpense", "40.20");
        request("DELETE", TRANSACTIONS + "/" + id, user.token(), null).status(204);
        request("DELETE", TRANSACTIONS + "/" + id, user.token(), null).status(404);
        request("GET", TRANSACTIONS + "/" + id, user.token(), null).status(404);
        balances(user).money("totalBalance", "350.05");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.01", "99999999999999999.99"})
    @DisplayName("it quando o valor é o mínimo ou máximo permitido, então persiste e calcula sem arredondamento")
    void validAmounts(String amount) throws Exception {
        var user = user();
        var account = account(user, "Conta", "0");
        var category = category(user, "Receita", "INCOME");
        var id = transaction(user, account, category, "INCOME", amount, LocalDate.now());
        request("GET", TRANSACTIONS + "/" + id, user.token(), null).money("amount", amount);
        balances(user).money("totalBalance", amount);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.01", "1.001", "100000000000000000.00"})
    @DisplayName("it quando o valor é zero, negativo ou excede precisão, então criação e atualização falham sem efeitos parciais")
    void invalidAmounts(String amount) throws Exception {
        var user = user();
        var account = account(user, "Conta", "100");
        var category = category(user, "Categoria", "EXPENSE");
        var id = transaction(user, account, category, "EXPENSE", "10.10", LocalDate.now());
        var bad = transactionBody(account, category, "EXPENSE", amount, LocalDate.now());
        request("POST", TRANSACTIONS, user.token(), bad).status(400);
        request("PUT", TRANSACTIONS + "/" + id, user.token(), bad).status(400);
        request("GET", TRANSACTIONS + "/" + id, user.token(), null).money("amount", "10.10");
        assertThat(request("GET", TRANSACTIONS, user.token(), null).list("content")).hasSize(1);
        balances(user).money("totalBalance", "89.90");
    }

    @Test
    @DisplayName("it quando faltam campos ou data e descrição são inválidas, então rejeita sem alterar o lançamento original")
    void fieldsDatesAndDescription() throws Exception {
        var user = user();
        var account = account(user, "Conta", "100");
        var category = category(user, "Categoria", "EXPENSE");
        var good = transactionBody(account, category, "EXPENSE", "10", LocalDate.now().minusDays(1));
        var id = transaction(user, account, category, "EXPENSE", "10", LocalDate.now().minusDays(1));
        var invalid = new ArrayList<Map<String, Object>>();
        for (String field : List.of("accountId", "categoryId", "type", "amount", "occurredOn")) {
            var body = new HashMap<>(good);
            body.remove(field);
            invalid.add(body);
        }
        for (String date : List.of(LocalDate.now().plusDays(1).toString(), "2026-02-30", "ontem")) {
            var body = new HashMap<>(good);
            body.put("occurredOn", date);
            invalid.add(body);
        }
        for (String description : List.of("", "   ", "d".repeat(256))) {
            var body = new HashMap<>(good);
            body.put("description", description);
            invalid.add(body);
        }
        for (var body : invalid) {
            request("POST", TRANSACTIONS, user.token(), body).status(400);
            request("PUT", TRANSACTIONS + "/" + id, user.token(), body).status(400);
        }
        for (String description : List.of("x", "d".repeat(255))) {
            var body = new HashMap<>(good);
            body.put("description", "  " + description + "  ");
            var reply = request("PUT", TRANSACTIONS + "/" + id, user.token(), body);
            reply.status(200);
            assertThat(reply.text("description")).isEqualTo(description);
        }
        assertThat(request("GET", TRANSACTIONS, user.token(), null).list("content")).hasSize(1);
        balances(user).money("totalBalance", "90");
    }

    @Test
    @DisplayName("it quando tipo é incompatível ou referências inexistem, então rejeita e preserva estado e saldo")
    void incompatibleReferences() throws Exception {
        var user = user();
        var account = account(user, "Conta", "100");
        var category = category(user, "Categoria", "EXPENSE");
        var id = transaction(user, account, category, "EXPENSE", "10", LocalDate.now());
        for (String method : List.of("POST", "PUT")) {
            String path = method.equals("POST") ? TRANSACTIONS : TRANSACTIONS + "/" + id;
            request(method, path, user.token(), transactionBody(account, category, "INCOME", "50", LocalDate.now())).status(409);
            request(method, path, user.token(), transactionBody(UUID.randomUUID().toString(), category, "EXPENSE", "50", LocalDate.now())).status(404);
            request(method, path, user.token(), transactionBody(account, UUID.randomUUID().toString(), "EXPENSE", "50", LocalDate.now())).status(404);
        }
        request("GET", TRANSACTIONS + "/" + id, user.token(), null).money("amount", "10");
        balances(user).money("totalBalance", "90");
    }

    @Test
    @DisplayName("it quando A tenta ler, alterar, excluir ou usar dados de B, então retorna 404 sem efeitos financeiros")
    void crossUserAccess() throws Exception {
        var a = user();
        var b = user();
        var accountA = account(a, "Conta A", "100");
        var accountB = account(b, "Conta B", "200");
        var categoryA = category(a, "Categoria A", "EXPENSE");
        var categoryB = category(b, "Categoria B", "EXPENSE");
        var txA = transaction(a, accountA, categoryA, "EXPENSE", "10", LocalDate.now());
        var txB = transaction(b, accountB, categoryB, "EXPENSE", "20", LocalDate.now());
        request("GET", TRANSACTIONS + "/" + txB, a.token(), null).status(404);
        request("DELETE", TRANSACTIONS + "/" + txB, a.token(), null).status(404);
        request("PUT", TRANSACTIONS + "/" + txB, a.token(), transactionBody(accountA, categoryA, "EXPENSE", "99", LocalDate.now())).status(404);
        for (String method : List.of("POST", "PUT")) {
            String path = method.equals("POST") ? TRANSACTIONS : TRANSACTIONS + "/" + txA;
            request(method, path, a.token(), transactionBody(accountB, categoryA, "EXPENSE", "99", LocalDate.now())).status(404);
            request(method, path, a.token(), transactionBody(accountA, categoryB, "EXPENSE", "99", LocalDate.now())).status(404);
        }
        balances(a).money("totalBalance", "90");
        balances(b).money("totalBalance", "180");
        request("GET", TRANSACTIONS + "/" + txB, b.token(), null).money("amount", "20");
    }

    @Test
    @DisplayName("it quando repete o mesmo PUT em sequência ou simultaneamente, então existe um único efeito financeiro")
    void repeatedUpdate() throws Exception {
        var user = user();
        var account = account(user, "Conta", "100");
        var category = category(user, "Categoria", "EXPENSE");
        var id = transaction(user, account, category, "EXPENSE", "10", LocalDate.now());
        var body = transactionBody(account, category, "EXPENSE", "20.01", LocalDate.now());
        request("PUT", TRANSACTIONS + "/" + id, user.token(), body).status(200);
        request("PUT", TRANSACTIONS + "/" + id, user.token(), body).status(200);
        simultaneously(() -> request("PUT", TRANSACTIONS + "/" + id, user.token(), body),
                () -> request("PUT", TRANSACTIONS + "/" + id, user.token(), body)).forEach(r -> r.status(200));
        assertThat(request("GET", TRANSACTIONS, user.token(), null).list("content")).hasSize(1);
        balances(user).money("totalBalance", "79.99");
    }

    @RepeatedTest(5)
    @DisplayName("it quando exclui o mesmo lançamento simultaneamente, então retorna 204 e 404 com um único efeito")
    void concurrentDelete() throws Exception {
        var user = user();
        var account = account(user, "Conta", "100");
        var category = category(user, "Categoria", "EXPENSE");
        var id = transaction(user, account, category, "EXPENSE", "10", LocalDate.now());
        var replies = simultaneously(() -> request("DELETE", TRANSACTIONS + "/" + id, user.token(), null),
                () -> request("DELETE", TRANSACTIONS + "/" + id, user.token(), null));
        assertThat(replies).extracting(Reply::code).containsExactlyInAnyOrder(204, 404);
        balances(user).money("totalBalance", "100");
    }

    @Test
    @DisplayName("it quando reenvia a mesma chave de idempotência, então mantém o mesmo ID e um único lançamento [D2]")
    void idempotentReplay() throws Exception {
        var user = user();
        var account = account(user, "Conta", "100");
        var category = category(user, "Categoria", "EXPENSE");
        var body = transactionBody(account, category, "EXPENSE", "25.10", LocalDate.now());
        String key = UUID.randomUUID().toString();
        var first = requestWithKey("POST", TRANSACTIONS, user.token(), body, key);
        first.status(201);
        var replay = requestWithKey("POST", TRANSACTIONS, user.token(), body, key);
        assertThat(replay.code()).isBetween(200, 299);
        assertThat(replay.text("id")).isEqualTo(first.text("id"));
        assertThat(request("GET", TRANSACTIONS, user.token(), null).list("content")).hasSize(1);
        balances(user).money("totalBalance", "74.90");
    }

    @Test
    @DisplayName("it quando a mesma chave é enviada simultaneamente, então só persiste um lançamento [D2]")
    void concurrentIdempotentReplay() throws Exception {
        var user = user();
        var account = account(user, "Conta", "100");
        var category = category(user, "Categoria", "EXPENSE");
        var body = transactionBody(account, category, "EXPENSE", "25.10", LocalDate.now());
        String key = UUID.randomUUID().toString();
        var replies = simultaneously(() -> requestWithKey("POST", TRANSACTIONS, user.token(), body, key),
                () -> requestWithKey("POST", TRANSACTIONS, user.token(), body, key));
        for (var reply : replies) assertThat(reply.code()).isBetween(200, 299);
        assertThat(replies.get(0).text("id")).isEqualTo(replies.get(1).text("id"));
        assertThat(request("GET", TRANSACTIONS, user.token(), null).list("content")).hasSize(1);
        balances(user).money("totalBalance", "74.90");
    }

    @Test
    @DisplayName("it quando operações distintas chegam juntas, então ambas são contabilizadas exatamente uma vez")
    void differentConcurrentTransactions() throws Exception {
        var user = user();
        var account = account(user, "Conta", "100");
        var category = category(user, "Categoria", "EXPENSE");
        var replies = simultaneously(
                () -> requestWithKey("POST", TRANSACTIONS, user.token(), transactionBody(account, category, "EXPENSE", "10.01", LocalDate.now()), UUID.randomUUID().toString()),
                () -> requestWithKey("POST", TRANSACTIONS, user.token(), transactionBody(account, category, "EXPENSE", "20.02", LocalDate.now()), UUID.randomUUID().toString()));
        replies.forEach(r -> r.status(201));
        assertThat(replies.get(0).text("id")).isNotEqualTo(replies.get(1).text("id"));
        assertThat(request("GET", TRANSACTIONS, user.token(), null).list("content")).hasSize(2);
        balances(user).money("totalBalance", "69.97");
    }

    @Test
    @DisplayName("it quando A e B usam a mesma chave, então as operações continuam isoladas por usuário")
    void idempotencyIsScopedToUser() throws Exception {
        var a = user();
        var b = user();
        String key = UUID.randomUUID().toString();
        for (var user : List.of(a, b)) {
            var account = account(user, "Conta", "100");
            var category = category(user, "Categoria", "EXPENSE");
            requestWithKey("POST", TRANSACTIONS, user.token(), transactionBody(account, category, "EXPENSE", "10", LocalDate.now()), key).status(201);
            assertThat(request("GET", TRANSACTIONS, user.token(), null).list("content")).hasSize(1);
            balances(user).money("totalBalance", "90");
        }
    }

    @Test
    @DisplayName("it quando duas edições disputam o lançamento, então ambas são aceitas e a última gravação prevalece [D3]")
    void concurrentLastWriteWins() throws Exception {
        var user = user();
        var first = account(user, "Primeira", "100");
        var second = account(user, "Segunda", "200");
        var category = category(user, "Categoria", "EXPENSE");
        var id = transaction(user, first, category, "EXPENSE", "1", LocalDate.now());
        var a = transactionBody(first, category, "EXPENSE", "10.01", LocalDate.now());
        var b = transactionBody(second, category, "EXPENSE", "20.02", LocalDate.now().minusDays(1));
        simultaneously(() -> request("PUT", TRANSACTIONS + "/" + id, user.token(), a),
                () -> request("PUT", TRANSACTIONS + "/" + id, user.token(), b)).forEach(r -> r.status(200));
        var found = request("GET", TRANSACTIONS + "/" + id, user.token(), null);
        var tuple = List.of(found.text("accountId"), found.value("amount").toString(), found.text("occurredOn"));
        assertThat(tuple).isIn(List.of(first, "10.01", LocalDate.now().toString()),
                List.of(second, "20.02", LocalDate.now().minusDays(1).toString()));
        request("PUT", TRANSACTIONS + "/" + id, user.token(), a).status(200);
        request("PUT", TRANSACTIONS + "/" + id, user.token(), b).status(200);
        var last = request("GET", TRANSACTIONS + "/" + id, user.token(), null);
        assertThat(last.text("accountId")).isEqualTo(second);
        last.money("amount", "20.02");
        balances(user).money("totalBalance", "279.98");
        assertThat(request("GET", TRANSACTIONS, user.token(), null).list("content")).hasSize(1);
    }
}
