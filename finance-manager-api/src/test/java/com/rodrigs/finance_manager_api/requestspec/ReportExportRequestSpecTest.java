package com.rodrigs.finance_manager_api.requestspec;

import com.rodrigs.finance_manager_api.report.dto.ReportExportFilter;
import com.rodrigs.finance_manager_api.report.service.ReportExportQueryService;
import com.rodrigs.finance_manager_api.shared.enums.TransactionType;
import com.rodrigs.finance_manager_api.support.HttpRequestSpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class ReportExportRequestSpecTest extends HttpRequestSpec {
    private static final LocalDate START = LocalDate.of(2025, 1, 1);
    private static final LocalDate END = LocalDate.of(2025, 1, 31);
    private static final String PATH = REPORTS + "/export?format=CSV&startDate=" + START + "&endDate=" + END;
    private static final String HEADER = "\uFEFFData;Descrição;Tipo;Conta;Categoria;Valor\r\n";
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ReportExportQueryService queryService;
    @LocalServerPort private int exportPort;

    @Test
    void shouldDownloadAllRowsInOrderWithHeadersAndConsistentTotals() throws Exception {
        var user = user();
        String account = account(user, "Conta principal", "999.00");
        String income = category(user, "Salário", "INCOME");
        String expense = category(user, "Alimentação", "EXPENSE");
        transaction(user, account, expense, "EXPENSE", "250.50", END);
        transaction(user, account, income, "INCOME", "1000.00", START);
        transaction(user, account, income, "INCOME", "10.00", START.minusDays(1));
        transaction(user, account, expense, "EXPENSE", "10.00", END.plusDays(1));

        var response = download(user.token(), PATH);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("text/csv;charset=UTF-8");
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(response.headers().firstValue("Content-Disposition"))
                .hasValue("attachment; filename=\"finance-manager_2025-01-01_2025-01-31.csv\"");
        assertThat(response.body()).isEqualTo(HEADER
                + "2025-01-01;;Receita;Conta principal;Salário;1000,00\r\n"
                + "2025-01-31;;Despesa;Conta principal;Alimentação;250,50\r\n");

        var snapshot = queryService.load(UUID.fromString(user.id()), new ReportExportFilter(START, END, null, null, null));
        assertThat(snapshot.rows()).hasSize(2);
        assertThat(snapshot.totalIncome()).isEqualByComparingTo("1000.00");
        assertThat(snapshot.totalExpense()).isEqualByComparingTo("250.50");
        assertThat(snapshot.netBalance()).isEqualByComparingTo("749.50");
    }

    @Test
    void shouldApplyEveryFilterAndExcludeOtherUsers() throws Exception {
        var owner = user();
        var other = user();
        String account = account(owner, "Conta A", "0");
        String otherAccount = account(owner, "Conta B", "0");
        String expense = category(owner, "Despesa A", "EXPENSE");
        String otherCategory = category(owner, "Despesa B", "EXPENSE");
        String income = category(owner, "Salário", "INCOME");
        transaction(owner, account, expense, "EXPENSE", "10", START);
        transaction(owner, otherAccount, expense, "EXPENSE", "20", START);
        transaction(owner, account, otherCategory, "EXPENSE", "30", START);
        transaction(owner, account, income, "INCOME", "40", START);
        transaction(other, account(other, "Segredo", "0"),
                category(other, "Privada", "EXPENSE"), "EXPENSE", "999", START);

        assertThat(download(owner.token(), PATH + "&type=EXPENSE&accountId=" + account + "&categoryId=" + expense).body())
                .isEqualTo(HEADER + "2025-01-01;;Despesa;Conta A;Despesa A;10,00\r\n");
        assertThat(download(owner.token(), PATH + "&type=INCOME").body())
                .isEqualTo(HEADER + "2025-01-01;;Receita;Conta A;Salário;40,00\r\n");
        assertThat(download(owner.token(), PATH + "&accountId=" + otherAccount).body())
                .isEqualTo(HEADER + "2025-01-01;;Despesa;Conta B;Despesa A;20,00\r\n");
        assertThat(download(owner.token(), PATH + "&categoryId=" + otherCategory).body())
                .isEqualTo(HEADER + "2025-01-01;;Despesa;Conta A;Despesa B;30,00\r\n");
        assertThat(download(owner.token(), PATH).body()).doesNotContain("Segredo", "Privada", "999,00");
        var data = queryService.load(UUID.fromString(owner.id()),
                new ReportExportFilter(START, END, TransactionType.EXPENSE, UUID.fromString(account), UUID.fromString(expense)));
        assertThat(data.totalIncome()).isEqualByComparingTo("0");
        assertThat(data.netBalance()).isEqualByComparingTo("-10");
    }

    @Test
    void shouldHideForeignAndMissingFilterResources() throws Exception {
        var owner = user();
        var other = user();
        String foreignAccount = account(other, "Privada", "0");
        String foreignCategory = category(other, "Privada", "EXPENSE");
        request("GET", PATH + "&accountId=" + foreignAccount, owner.token(), null).status(404);
        request("GET", PATH + "&categoryId=" + foreignCategory, owner.token(), null).status(404);
        request("GET", PATH + "&accountId=" + UUID.randomUUID(), owner.token(), null).status(404);
        request("GET", PATH + "&categoryId=" + UUID.randomUUID(), owner.token(), null).status(404);
    }

    @Test
    void shouldReturnHeaderOnlyForEmptyResultAndRequireAuthentication() throws Exception {
        var owner = user();
        assertThat(download(owner.token(), PATH).body()).isEqualTo(HEADER);
        request("GET", PATH, null, null).status(401);
        request("GET", PATH, "invalid-token", null).status(401);
    }

    @Test
    void shouldRejectInvalidParametersAndAcceptExactly366Days() throws Exception {
        var owner = user();
        for (String query : new String[]{
                "format=CSV&startDate=2025-01-01", "startDate=2025-01-01&endDate=2025-01-31",
                "format=CSV&startDate=invalid&endDate=2025-01-31",
                "format=PDF&startDate=2025-01-01&endDate=2025-01-31",
                "format=INVALID&startDate=2025-01-01&endDate=2025-01-31"}) {
            request("GET", REPORTS + "/export?" + query, owner.token(), null).status(400);
        }
        request("GET", PATH + "&type=INVALID", owner.token(), null).status(400);
        request("GET", PATH + "&accountId=invalid", owner.token(), null).status(400);
        var inverted = request("GET", REPORTS + "/export?format=CSV&startDate=" + END + "&endDate=" + START, owner.token(), null);
        inverted.status(400);
        assertThat(inverted.text("code")).isEqualTo("INVALID_TRANSACTION_DATE_RANGE");
        request("GET", REPORTS + "/export?format=CSV&startDate=" + START + "&endDate=" + START.plusDays(365), owner.token(), null).status(200);
        var excess = request("GET", REPORTS + "/export?format=CSV&startDate=" + START + "&endDate=" + START.plusDays(366), owner.token(), null);
        excess.status(400);
        assertThat(excess.text("code")).isEqualTo("REPORT_EXPORT_LIMIT_EXCEEDED");
    }

    @Test
    void shouldAccept5000RowsAndReject5001WithoutPartialFile() throws Exception {
        var owner = user();
        String account = account(owner, "Conta", "0");
        String category = category(owner, "Receita", "INCOME");
        jdbc.update("""
                INSERT INTO transactions(user_id, account_id, category_id, type, amount, occurred_on)
                SELECT ?, ?, ?, 'INCOME', 1.00, DATE '2025-01-01' FROM generate_series(1, 5000)
                """, UUID.fromString(owner.id()), UUID.fromString(account), UUID.fromString(category));
        var response = download(owner.token(), PATH);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().lines().count()).isEqualTo(5001);
        transaction(owner, account, category, "INCOME", "1", START);
        var excess = download(owner.token(), PATH);
        assertThat(excess.statusCode()).isEqualTo(400);
        assertThat(excess.headers().firstValue("Content-Type").orElseThrow()).contains("application/problem+json");
        assertThat(excess.headers().firstValue("Content-Disposition")).isEmpty();
        assertThat(excess.body()).contains("REPORT_EXPORT_LIMIT_EXCEEDED").doesNotContain(HEADER);
    }

    @Test
    void shouldUseIdAsTieBreakerAndRenderEscapedTextFromDatabase() throws Exception {
        var owner = user();
        String account = account(owner, "Conta; principal", "0");
        String category = category(owner, "=1+1", "INCOME");
        String first = transaction(owner, account, category, "INCOME", "1", START);
        String second = transaction(owner, account, category, "INCOME", "2", START);
        jdbc.update("UPDATE transactions SET description = ? WHERE id = ?", "Café; \"especial\"\nlinha 2", UUID.fromString(first));
        jdbc.update("UPDATE transactions SET description = ? WHERE id = ?", "  +SUM(A1)", UUID.fromString(second));
        var expectedAmounts = jdbc.queryForList("SELECT amount FROM transactions ORDER BY occurred_on, id", java.math.BigDecimal.class);
        String body = download(owner.token(), PATH).body();
        assertThat(body).contains("\"Café; \"\"especial\"\"\nlinha 2\"", "'  +SUM(A1)", "\"Conta; principal\"", "'=1+1");
        assertThat(body.indexOf(";" + expectedAmounts.get(0).toPlainString().replace('.', ',') + "\r\n"))
                .isLessThan(body.indexOf(";" + expectedAmounts.get(1).toPlainString().replace('.', ',') + "\r\n"));
    }

    private HttpResponse<String> download(String token, String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + exportPort + path))
                .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + token)
                .header("Accept", "text/csv").GET().build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
