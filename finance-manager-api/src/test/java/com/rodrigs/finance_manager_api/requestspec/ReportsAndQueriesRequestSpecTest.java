package com.rodrigs.finance_manager_api.requestspec;

import com.rodrigs.finance_manager_api.support.HttpRequestSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("describe Filtros, paginação e relatórios por HTTP")
class ReportsAndQueriesRequestSpecTest extends HttpRequestSpec {
    @Test
    @DisplayName("it quando pagina e combina filtros, então respeita limites inclusivos, ordenação e isolamento entre usuários")
    void paginationAndFilters() throws Exception {
        var a = user();
        var b = user();
        var account = account(a, "Conta A", "0");
        var anotherAccount = account(a, "Conta extra", "0");
        var expense = category(a, "Despesa A", "EXPENSE");
        var anotherExpense = category(a, "Outra despesa", "EXPENSE");
        var income = category(a, "Receita A", "INCOME");
        var bAccount = account(b, "Conta B", "0");
        var bCategory = category(b, "Despesa B", "EXPENSE");
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(2);
        String oldest = transaction(a, account, expense, "EXPENSE", "1.01", start);
        String middle = transaction(a, account, expense, "EXPENSE", "2.02", start.plusDays(1));
        String newest = transaction(a, account, expense, "EXPENSE", "3.03", end);
        transaction(a, anotherAccount, expense, "EXPENSE", "4", end);
        transaction(a, account, anotherExpense, "EXPENSE", "5", end);
        transaction(a, account, income, "INCOME", "6", end);
        transaction(a, account, expense, "EXPENSE", "7", start.minusDays(1));
        transaction(b, bAccount, bCategory, "EXPENSE", "999", end);
        String filter = period(start, end) + "&accountId=" + account + "&categoryId=" + expense + "&type=EXPENSE&size=2";
        var first = request("GET", TRANSACTIONS + filter + "&page=0", a.token(), null);
        first.status(200);
        assertThat(first.value("totalElements")).isEqualTo(3);
        assertThat(first.value("totalPages")).isEqualTo(2);
        assertThat(first.value("number")).isEqualTo(0);
        assertThat(first.text("content.0.id")).isEqualTo(newest);
        assertThat(first.text("content.1.id")).isEqualTo(middle);
        var last = request("GET", TRANSACTIONS + filter + "&page=1", a.token(), null);
        last.status(200);
        assertThat(last.text("content.0.id")).isEqualTo(oldest);
        var beyond = request("GET", TRANSACTIONS + filter + "&page=2", a.token(), null);
        beyond.status(200);
        assertThat(beyond.list("content")).isEmpty();
        assertThat(beyond.value("totalElements")).isEqualTo(3);
        var defaults = request("GET", TRANSACTIONS, a.token(), null);
        defaults.status(200);
        assertThat(defaults.value("size")).isEqualTo(20);
        assertThat(defaults.value("totalElements")).isEqualTo(7);
        assertThat(defaults.body()).doesNotContain(bAccount, bCategory, "999");
        for (String foreign : List.of("accountId=" + bAccount, "categoryId=" + bCategory)) {
            request("GET", TRANSACTIONS + "?" + foreign, a.token(), null).status(404);
        }
    }

    @Test
    @DisplayName("it quando duas transações têm a mesma data de ocorrência, então a mais recentemente criada vem primeiro")
    void creationOrderTieBreaker() throws Exception {
        var user = user();
        var account = account(user, "Conta", "0");
        var category = category(user, "Receita", "INCOME");
        String first = transaction(user, account, category, "INCOME", "1", LocalDate.now());
        String second = transaction(user, account, category, "INCOME", "2", LocalDate.now());
        var listing = request("GET", TRANSACTIONS, user.token(), null);
        listing.status(200);
        assertThat(listing.text("content.0.id")).isEqualTo(second);
        assertThat(listing.text("content.1.id")).isEqualTo(first);
    }

    @ParameterizedTest
    @ValueSource(strings = {"?startDate=2026-01-02&endDate=2026-01-01", "?accountId=invalido", "?categoryId=invalido",
            "?type=INVALID", "?startDate=ontem", "?endDate=2026-02-30"})
    @DisplayName("it quando o filtro é inválido, então retorna 400")
    void invalidFilters(String query) throws Exception {
        var user = user();
        request("GET", TRANSACTIONS + query, user.token(), null).status(400);
    }

    @Test
    @DisplayName("it quando não há contas ou movimentos, então retorna listas vazias, totais zero e dias do fluxo preenchidos")
    void emptyData() throws Exception {
        var user = user();
        assertThat(request("GET", TRANSACTIONS, user.token(), null).list("content")).isEmpty();
        assertThat(request("GET", ACCOUNTS, user.token(), null).list("")).isEmpty();
        assertThat(request("GET", CATEGORIES, user.token(), null).list("")).isEmpty();
        var balances = balances(user);
        balances.money("totalBalance", "0");
        assertThat(balances.list("accounts")).isEmpty();
        var today = LocalDate.now();
        for (String report : List.of("summary", "cash-flow", "expenses-by-category", "category-breakdown")) {
            var reply = request("GET", REPORTS + "/" + report + period(today.minusDays(2), today) + "&type=INCOME", user.token(), null);
            reply.status(200);
            switch (report) {
                case "summary" -> {
                    reply.money("totalIncome", "0");
                    reply.money("totalExpense", "0");
                    reply.money("netBalance", "0");
                }
                case "cash-flow" -> {
                    assertThat(reply.list("points")).hasSize(3);
                    for (int i = 0; i < 3; i++) {
                        assertThat(reply.text("points." + i + ".date")).isEqualTo(today.minusDays(2 - i).toString());
                        reply.money("points." + i + ".totalIncome", "0");
                        reply.money("points." + i + ".totalExpense", "0");
                        reply.money("points." + i + ".netBalance", "0");
                    }
                }
                default -> {
                    assertThat(reply.list("categories")).isEmpty();
                    reply.money(report.equals("category-breakdown") ? "totalAmount" : "totalExpense", "0");
                }
            }
        }
    }

    @Test
    @DisplayName("it quando consolida dinheiro e categorias, então inclui centavos, contas sem movimentos e saldo negativo sem vazar outro usuário")
    void accurateReportsAndIsolation() throws Exception {
        var a = user();
        var b = user();
        var primary = account(a, "Conta principal", "10.00");
        var secondary = account(a, "Conta extra", "5.00");
        account(a, "Sem lançamentos", "-2.00");
        var income = category(a, "Receita própria", "INCOME");
        var food = category(a, "Alimentação", "EXPENSE");
        var transport = category(a, "Transporte", "EXPENSE");
        var bAccount = account(b, "Conta secreta B", "9999");
        var bCategory = category(b, "Categoria secreta B", "EXPENSE");
        var today = LocalDate.now();
        var start = today.minusDays(2);
        transaction(a, primary, income, "INCOME", "0.10", start);
        transaction(a, secondary, income, "INCOME", "0.20", today);
        transaction(a, primary, food, "EXPENSE", "10.11", start);
        transaction(a, secondary, food, "EXPENSE", "20.22", today);
        transaction(a, primary, transport, "EXPENSE", "5.05", today);
        transaction(a, primary, income, "INCOME", "1.00", start.minusDays(1));
        var foreign = transaction(b, bAccount, bCategory, "EXPENSE", "1234.56", today);
        String range = period(start, today);
        var summary = request("GET", REPORTS + "/summary" + range, a.token(), null);
        summary.status(200);
        summary.money("totalIncome", "0.30");
        summary.money("totalExpense", "35.38");
        summary.money("netBalance", "-35.08");
        var balances = balances(a);
        balances.money("totalBalance", "-21.08");
        balances.money("accounts.2.balance", "-2");
        var flow = request("GET", REPORTS + "/cash-flow" + range, a.token(), null);
        flow.status(200);
        assertThat(flow.list("points")).hasSize(3);
        flow.money("points.0.totalIncome", "0.10");
        flow.money("points.0.totalExpense", "10.11");
        flow.money("points.1.netBalance", "0");
        flow.money("points.2.totalExpense", "25.27");
        var expenses = request("GET", REPORTS + "/expenses-by-category" + range, a.token(), null);
        expenses.status(200);
        expenses.money("totalExpense", "35.38");
        assertThat(expenses.list("categories")).hasSize(2);
        assertThat(expenses.text("categories.0.categoryId")).isEqualTo(food);
        expenses.money("categories.0.totalExpense", "30.33");
        assertThat(expenses.text("categories.1.categoryId")).isEqualTo(transport);
        var expenseBreakdown = request("GET", REPORTS + "/category-breakdown" + range + "&type=EXPENSE", a.token(), null);
        expenseBreakdown.status(200);
        expenseBreakdown.money("totalAmount", "35.38");
        var incomeBreakdown = request("GET", REPORTS + "/category-breakdown" + range + "&type=INCOME", a.token(), null);
        incomeBreakdown.status(200);
        incomeBreakdown.money("totalAmount", "0.30");
        assertThat(incomeBreakdown.list("categories")).hasSize(1);
        assertThat(incomeBreakdown.text("categories.0.categoryId")).isEqualTo(income);
        for (var reply : List.of(summary, balances, flow, expenses, expenseBreakdown, incomeBreakdown)) {
            assertThat(reply.body()).doesNotContain(b.id(), bAccount, bCategory, foreign, "secreta B", "1234.56");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"summary", "cash-flow", "expenses-by-category", "category-breakdown"})
    @DisplayName("it quando faltam datas, o intervalo é invertido ou o tipo é inválido, então relatório retorna 400")
    void invalidReportParameters(String report) throws Exception {
        var user = user();
        for (String query : List.of("", "?startDate=2026-01-01", "?endDate=2026-01-02",
                "?startDate=ontem&endDate=2026-01-02", "?startDate=2026-01-02&endDate=2026-01-01")) {
            String separator = query.isEmpty() ? "?" : "&";
            request("GET", REPORTS + "/" + report + query + separator + "type=EXPENSE", user.token(), null).status(400);
        }
        if (report.equals("category-breakdown")) {
            request("GET", REPORTS + "/" + report + period(LocalDate.now(), LocalDate.now()), user.token(), null).status(400);
            request("GET", REPORTS + "/" + report + period(LocalDate.now(), LocalDate.now()) + "&type=INVALID", user.token(), null).status(400);
        }
    }

    @Test
    @DisplayName("it quando repete consultas e relatórios simultaneamente, então não modifica dados ou totais")
    void repeatedReads() throws Exception {
        var user = user();
        var account = account(user, "Conta", "100");
        var category = category(user, "Categoria", "EXPENSE");
        transaction(user, account, category, "EXPENSE", "10.01", LocalDate.now());
        var routes = new ArrayList<>(List.of(TRANSACTIONS, ACCOUNTS, CATEGORIES, REPORTS + "/balances"));
        for (String report : List.of("summary", "cash-flow", "expenses-by-category", "category-breakdown")) {
            routes.add(REPORTS + "/" + report + period(LocalDate.now(), LocalDate.now()) + "&type=EXPENSE");
        }
        for (String route : routes) {
            var before = request("GET", route, user.token(), null);
            before.status(200);
            var replies = simultaneously(() -> request("GET", route, user.token(), null),
                    () -> request("GET", route, user.token(), null));
            for (var reply : replies) {
                reply.status(200);
                assertThat(reply.value("")).isEqualTo(before.value(""));
            }
        }
        balances(user).money("totalBalance", "89.99");
        assertThat(request("GET", TRANSACTIONS, user.token(), null).list("content")).hasSize(1);
    }
}
