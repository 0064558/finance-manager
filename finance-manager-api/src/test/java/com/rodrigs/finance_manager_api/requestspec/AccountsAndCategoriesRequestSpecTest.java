package com.rodrigs.finance_manager_api.requestspec;

import com.rodrigs.finance_manager_api.support.HttpRequestSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("describe Contas e categorias por HTTP")
class AccountsAndCategoriesRequestSpecTest extends HttpRequestSpec {
    @ParameterizedTest
    @ValueSource(strings = {"0.00", "-123.45", "99999999999999999.99", "-99999999999999999.99"})
    @DisplayName("it quando saldo inicial é zero, negativo ou está no limite, então preserva o valor exato")
    void accountBalanceLimits(String balance) throws Exception {
        var user = user();
        var id = account(user, "  Conta principal  ", balance);
        var found = request("GET", ACCOUNTS + "/" + id, user.token(), null);
        found.status(200);
        assertThat(found.text("name")).isEqualTo("Conta principal");
        found.money("initialBalance", balance);
        balances(user).money("totalBalance", balance);
    }

    @ParameterizedTest
    @CsvSource({"ACCOUNT, name", "ACCOUNT, type", "ACCOUNT, initialBalance", "CATEGORY, name", "CATEGORY, transactionType"})
    @DisplayName("it quando omite campo obrigatório, então retorna 400 sem criar recurso")
    void missingField(String kind, String field) throws Exception {
        var user = user();
        String route = kind.equals("ACCOUNT") ? ACCOUNTS : CATEGORIES;
        var body = new HashMap<>(kind.equals("ACCOUNT") ? accountBody("Conta", "CASH", "1.00") : categoryBody("Categoria", "EXPENSE"));
        body.remove(field);
        request("POST", route, user.token(), body).status(400);
        var listing = request("GET", route, user.token(), null);
        listing.status(200);
        assertThat(listing.list("")).isEmpty();
    }

    @Test
    @DisplayName("it quando nome, tipo ou precisão são inválidos, então rejeita criação e atualização preservando o recurso")
    void invalidInputs() throws Exception {
        var user = user();
        var id = account(user, "Original", "100.00");
        var invalidAccounts = List.of(accountBody("A", "CASH", "1"), accountBody(" ", "CASH", "1"),
                accountBody("A".repeat(101), "CASH", "1"), accountBody("Conta", "INVALID", "1"),
                accountBody("Conta", "CASH", "1.001"), accountBody("Conta", "CASH", "100000000000000000.00"));
        for (var body : invalidAccounts) {
            request("POST", ACCOUNTS, user.token(), body).status(400);
            request("PUT", ACCOUNTS + "/" + id, user.token(), body).status(400);
        }
        var found = request("GET", ACCOUNTS + "/" + id, user.token(), null);
        assertThat(found.text("name")).isEqualTo("Original");
        found.money("initialBalance", "100.00");
        assertThat(request("GET", ACCOUNTS, user.token(), null).list("")).hasSize(1);
        var category = category(user, "Original", "EXPENSE");
        for (var body : List.of(categoryBody("A", "EXPENSE"), categoryBody(" ", "EXPENSE"),
                categoryBody("A".repeat(81), "EXPENSE"), categoryBody("Categoria", "INVALID"))) {
            request("POST", CATEGORIES, user.token(), body).status(400);
            request("PUT", CATEGORIES + "/" + category, user.token(), body).status(400);
        }
        assertThat(request("GET", CATEGORIES + "/" + category, user.token(), null).text("name")).isEqualTo("Original");
    }

    @Test
    @DisplayName("it quando os nomes estão nos limites de tamanho, então aceita sem truncar")
    void nameBoundaries() throws Exception {
        var user = user();
        for (var name : List.of("Ab", "N".repeat(100))) {
            var id = account(user, name, "0");
            assertThat(request("GET", ACCOUNTS + "/" + id, user.token(), null).text("name")).isEqualTo(name);
        }
        for (var name : List.of("Ab", "N".repeat(80))) {
            var id = category(user, name, "EXPENSE");
            assertThat(request("GET", CATEGORIES + "/" + id, user.token(), null).text("name")).isEqualTo(name);
        }
    }

    @Test
    @DisplayName("it quando repete o cadastro de conta com os mesmos dados, então rejeita a segunda com 409 [D1]")
    void duplicateAccount() throws Exception {
        var user = user();
        var body = accountBody("Conta repetida", "CHECKING", "100.00");
        request("POST", ACCOUNTS, user.token(), body).status(201);
        request("POST", ACCOUNTS, user.token(), body).status(409);
        assertThat(request("GET", ACCOUNTS, user.token(), null).list("")).hasSize(1);
        balances(user).money("totalBalance", "100.00");
    }

    @Test
    @DisplayName("it quando cria contas idênticas simultaneamente, então apenas uma é persistida [D1]")
    void concurrentDuplicateAccounts() throws Exception {
        var user = user();
        var body = accountBody("Conta repetida", "CHECKING", "100.00");
        var replies = simultaneously(() -> request("POST", ACCOUNTS, user.token(), body),
                () -> request("POST", ACCOUNTS, user.token(), body));
        assertThat(replies).extracting(Reply::code).containsExactlyInAnyOrder(201, 409);
        assertThat(request("GET", ACCOUNTS, user.token(), null).list("")).hasSize(1);
    }

    @Test
    @DisplayName("it quando A tenta ler, alterar, excluir ou criar recurso em nome de B, então os dados de B ficam intactos")
    void ownership() throws Exception {
        var a = user();
        var b = user();
        var account = account(b, "Conta privada B", "500.00");
        var category = category(b, "Categoria privada B", "EXPENSE");
        for (var pair : List.of(new String[]{ACCOUNTS, account}, new String[]{CATEGORIES, category})) {
            String path = pair[0] + "/" + pair[1];
            request("GET", path, a.token(), null).status(404);
            request("PUT", path, a.token(), pair[0].equals(ACCOUNTS)
                    ? accountBody("Tentativa", "CASH", "999.00") : categoryBody("Tentativa", "INCOME")).status(404);
            request("DELETE", path, a.token(), null).status(404);
            assertThat(request("GET", pair[0], a.token(), null).list("")).isEmpty();
            var body = new HashMap<>(pair[0].equals(ACCOUNTS)
                    ? accountBody("Recurso de A", "CASH", "1.00") : categoryBody("Recurso de A", "INCOME"));
            body.put("userId", b.id());
            request("POST", pair[0], a.token(), body).status(201);
            assertThat(request("GET", pair[0], b.token(), null).list("")).hasSize(1);
            assertThat(request("GET", pair[0], a.token(), null).list("")).hasSize(1);
        }
        assertThat(request("GET", ACCOUNTS + "/" + account, b.token(), null).text("name")).isEqualTo("Conta privada B");
        assertThat(request("GET", CATEGORIES + "/" + category, b.token(), null).text("transactionType")).isEqualTo("EXPENSE");
        balances(b).money("totalBalance", "500.00");
    }

    @Test
    @DisplayName("it quando edita sem histórico e repete o PUT, então mantém o recurso único; quando exclui, então a repetição retorna 404")
    void updatesAndDeletes() throws Exception {
        var user = user();
        var account = account(user, "Conta", "10");
        var category = category(user, "Categoria", "INCOME");
        for (int i = 0; i < 2; i++) {
            request("PUT", ACCOUNTS + "/" + account, user.token(), accountBody("Nova conta", "SAVINGS", "-20.05")).status(200);
            request("PUT", CATEGORIES + "/" + category, user.token(), categoryBody("Nova categoria", "EXPENSE")).status(200);
        }
        balances(user).money("totalBalance", "-20.05");
        for (var pair : List.of(new String[]{ACCOUNTS, account}, new String[]{CATEGORIES, category})) {
            assertThat(request("GET", pair[0], user.token(), null).list("")).hasSize(1);
            request("DELETE", pair[0] + "/" + pair[1], user.token(), null).status(204);
            request("DELETE", pair[0] + "/" + pair[1], user.token(), null).status(404);
            request("GET", pair[0] + "/" + pair[1], user.token(), null).status(404);
        }
    }

    @Test
    @DisplayName("it quando duplica categoria por usuário e tipo, então retorna 409; outros tipos e usuários são independentes")
    void categoryUniqueness() throws Exception {
        var a = user();
        var b = user();
        category(a, "Mercado", "EXPENSE");
        request("POST", CATEGORIES, a.token(), categoryBody("  MERCADO  ", "EXPENSE")).status(409);
        category(a, "Mercado", "INCOME");
        category(b, "Mercado", "EXPENSE");
        var other = category(a, "Outra", "EXPENSE");
        request("PUT", CATEGORIES + "/" + other, a.token(), categoryBody("Mercado", "EXPENSE")).status(409);
        assertThat(request("GET", CATEGORIES + "/" + other, a.token(), null).text("name")).isEqualTo("Outra");
    }

    @Test
    @DisplayName("it quando cadastra categorias iguais simultaneamente, então uma cria e a outra recebe 409")
    void concurrentCategories() throws Exception {
        var user = user();
        var body = categoryBody("Mercado", "EXPENSE");
        var replies = simultaneously(() -> request("POST", CATEGORIES, user.token(), body),
                () -> request("POST", CATEGORIES, user.token(), body));
        assertThat(replies).extracting(Reply::code).containsExactlyInAnyOrder(201, 409);
        assertThat(request("GET", CATEGORIES, user.token(), null).list("")).hasSize(1);
    }

    @Test
    @DisplayName("it quando existe histórico, então rejeita operações proibidas e permite renomear sem alterar valores ou vínculos")
    void history() throws Exception {
        var user = user();
        var account = account(user, "Conta", "100");
        var category = category(user, "Mercado", "EXPENSE");
        var tx = transaction(user, account, category, "EXPENSE", "25.10", LocalDate.now());
        request("PUT", ACCOUNTS + "/" + account, user.token(), accountBody("Alterada", "CASH", "200")).status(409);
        request("DELETE", ACCOUNTS + "/" + account, user.token(), null).status(409);
        request("PUT", CATEGORIES + "/" + category, user.token(), categoryBody("Alterada", "INCOME")).status(409);
        request("DELETE", CATEGORIES + "/" + category, user.token(), null).status(409);
        assertThat(request("GET", ACCOUNTS + "/" + account, user.token(), null).text("name")).isEqualTo("Conta");
        assertThat(request("GET", CATEGORIES + "/" + category, user.token(), null).text("name")).isEqualTo("Mercado");
        request("PUT", ACCOUNTS + "/" + account, user.token(), accountBody("Renomeada", "SAVINGS", "100.00")).status(200);
        request("PUT", CATEGORIES + "/" + category, user.token(), categoryBody("Renomeada", "EXPENSE")).status(200);
        request("GET", TRANSACTIONS + "/" + tx, user.token(), null).status(200);
        balances(user).money("totalBalance", "74.90");
    }

    @RepeatedTest(5)
    @DisplayName("it quando a exclusão de conta ou categoria disputa com lançamento, então o resultado preserva os vínculos")
    void deleteRacesWithTransaction() throws Exception {
        var user = user();
        for (String base : List.of(ACCOUNTS, CATEGORIES)) {
            var account = account(user, "Conta " + base.substring(base.lastIndexOf('/') + 1), "100");
            var category = category(user, "Categoria " + base.substring(base.lastIndexOf('/') + 1), "EXPENSE");
            String id = base.equals(ACCOUNTS) ? account : category;
            var replies = simultaneously(() -> request("DELETE", base + "/" + id, user.token(), null),
                    () -> request("POST", TRANSACTIONS, user.token(), transactionBody(account, category, "EXPENSE", "10", LocalDate.now())));
            int deleted = replies.get(0).code();
            int created = replies.get(1).code();
            // The owner approved 404 or 409 when deletion wins, while still requiring atomicity.
            assertThat(List.of(deleted, created)).as("delete/create HTTP statuses")
                    .isIn(List.of(204, 404), List.of(204, 409), List.of(409, 201));
            request("GET", base + "/" + id, user.token(), null).status(created == 201 ? 200 : 404);
            var listing = request("GET", TRANSACTIONS, user.token(), null);
            listing.status(200);
            var linkedTransactions = listing.list("content").stream()
                    .map(row -> (Map<?, ?>) row)
                    .filter(row -> id.equals(row.get(base.equals(ACCOUNTS) ? "accountId" : "categoryId")))
                    .toList();
            assertThat(linkedTransactions).hasSize(created == 201 ? 1 : 0);
            if (created == 201) {
                var persisted = request("GET", TRANSACTIONS + "/" + replies.get(1).text("id"), user.token(), null);
                persisted.status(200);
                assertThat(persisted.text("accountId")).isEqualTo(account);
                assertThat(persisted.text("categoryId")).isEqualTo(category);
                persisted.money("amount", "10");
            }
        }
    }

    @RepeatedTest(5)
    @DisplayName("it quando muda o tipo da categoria ao mesmo tempo que lança, então nunca persiste categoria e transação incompatíveis")
    void typeChangeRace() throws Exception {
        var user = user();
        var account = account(user, "Conta", "100");
        var category = category(user, "Categoria", "EXPENSE");
        var replies = simultaneously(
                () -> request("PUT", CATEGORIES + "/" + category, user.token(), categoryBody("Categoria", "INCOME")),
                () -> request("POST", TRANSACTIONS, user.token(), transactionBody(account, category, "EXPENSE", "10", LocalDate.now())));
        assertThat(List.of(replies.get(0).code(), replies.get(1).code())).isIn(List.of(200, 409), List.of(409, 201));
        if (replies.get(1).code() == 201) {
            assertThat(request("GET", CATEGORIES + "/" + category, user.token(), null).text("transactionType")).isEqualTo("EXPENSE");
        }
    }

    @RepeatedTest(5)
    @DisplayName("it quando saldo inicial e primeiro lançamento mudam juntos, então o resultado corresponde a uma ordem válida")
    void balanceChangeRace() throws Exception {
        var user = user();
        var account = account(user, "Conta", "100");
        var category = category(user, "Categoria", "EXPENSE");
        var replies = simultaneously(
                () -> request("PUT", ACCOUNTS + "/" + account, user.token(), accountBody("Conta", "CHECKING", "200")),
                () -> request("POST", TRANSACTIONS, user.token(), transactionBody(account, category, "EXPENSE", "10", LocalDate.now())));
        replies.get(1).status(201);
        assertThat(replies.get(0).code()).isIn(200, 409);
        String initial = replies.get(0).code() == 200 ? "200" : "100";
        request("GET", ACCOUNTS + "/" + account, user.token(), null).money("initialBalance", initial);
        balances(user).money("totalBalance", replies.get(0).code() == 200 ? "190" : "90");
    }
}
