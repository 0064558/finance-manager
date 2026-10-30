package com.rodrigs.finance_manager_api.requestspec;

import com.rodrigs.finance_manager_api.report.dto.ReportExportFilter;
import com.rodrigs.finance_manager_api.report.service.ReportExportQueryService;
import com.rodrigs.finance_manager_api.shared.enums.TransactionType;
import com.rodrigs.finance_manager_api.support.HttpRequestSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import java.net.URI;
import java.io.ByteArrayInputStream;
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

    @ParameterizedTest
    @ValueSource(strings = {"CSV", "XLSX", "PDF"})
    void shouldHideForeignAndMissingFilterResources(String format) throws Exception {
        var owner = user();
        var other = user();
        String foreignAccount = account(other, "Privada", "0");
        String foreignCategory = category(other, "Privada", "EXPENSE");
        String path = PATH.replace("format=CSV", "format=" + format);
        request("GET", path + "&accountId=" + foreignAccount, owner.token(), null).status(404);
        request("GET", path + "&categoryId=" + foreignCategory, owner.token(), null).status(404);
        request("GET", path + "&accountId=" + UUID.randomUUID(), owner.token(), null).status(404);
        request("GET", path + "&categoryId=" + UUID.randomUUID(), owner.token(), null).status(404);
    }

    @Test
    void shouldReturnHeaderOnlyForEmptyResultAndRequireAuthentication() throws Exception {
        var owner = user();
        assertThat(download(owner.token(), PATH).body()).isEqualTo(HEADER);
        request("GET", PATH, null, null).status(401);
        request("GET", PATH, "invalid-token", null).status(401);
        request("GET", PATH.replace("format=CSV", "format=XLSX"), null, null).status(401);
        request("GET", PATH.replace("format=CSV", "format=XLSX"), "invalid-token", null).status(401);
        request("GET", PATH.replace("format=CSV", "format=PDF"), null, null).status(401);
        request("GET", PATH.replace("format=CSV", "format=PDF"), "invalid-token", null).status(401);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CSV", "XLSX", "PDF"})
    void shouldRejectInvalidParametersAndAcceptExactly366Days(String format) throws Exception {
        var owner = user();
        for (String query : new String[]{
                "format=CSV&startDate=2025-01-01", "startDate=2025-01-01&endDate=2025-01-31",
                "format=CSV&startDate=invalid&endDate=2025-01-31",
                "format=INVALID&startDate=2025-01-01&endDate=2025-01-31"}) {
            request("GET", REPORTS + "/export?" + query.replace("format=CSV", "format=" + format), owner.token(), null).status(400);
        }
        String path = PATH.replace("format=CSV", "format=" + format);
        request("GET", path + "&type=INVALID", owner.token(), null).status(400);
        request("GET", path + "&accountId=invalid", owner.token(), null).status(400);
        var inverted = request("GET", REPORTS + "/export?format=" + format + "&startDate=" + END + "&endDate=" + START, owner.token(), null);
        inverted.status(400);
        assertThat(inverted.text("code")).isEqualTo("INVALID_TRANSACTION_DATE_RANGE");
        request("GET", REPORTS + "/export?format=" + format + "&startDate=" + START + "&endDate=" + START.plusDays(365), owner.token(), null).status(200);
        var excess = request("GET", REPORTS + "/export?format=" + format + "&startDate=" + START + "&endDate=" + START.plusDays(366), owner.token(), null);
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
        var xlsx = downloadXlsx(owner.token(), "");
        assertThat(xlsx.statusCode()).isEqualTo(200);
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx.body()))) {
            assertThat(workbook.getSheet("Transações").getLastRowNum()).isEqualTo(5000);
            assertThat(workbook.getSheet("Resumo").getRow(10).getCell(1).getNumericCellValue()).isEqualTo(5000);
        }
        var pdf = downloadPdf(owner.token(), "");
        assertThat(pdf.statusCode()).isEqualTo(200);
        try (var reader = new PdfReader(pdf.body())) {
            assertThat(reader.getNumberOfPages()).isGreaterThan(1);
            assertThat(new PdfTextExtractor(reader).getTextFromPage(1)).contains("5000", "R$ 5.000,00");
        }
        transaction(owner, account, category, "INCOME", "1", START);
        var excess = download(owner.token(), PATH);
        assertThat(excess.statusCode()).isEqualTo(400);
        assertThat(excess.headers().firstValue("Content-Type").orElseThrow()).contains("application/problem+json");
        assertThat(excess.headers().firstValue("Content-Disposition")).isEmpty();
        assertThat(excess.body()).contains("REPORT_EXPORT_LIMIT_EXCEEDED").doesNotContain(HEADER);
        var xlsxExcess = downloadXlsx(owner.token(), "");
        assertThat(xlsxExcess.statusCode()).isEqualTo(400);
        assertThat(xlsxExcess.headers().firstValue("Content-Disposition")).isEmpty();
        assertThat(new String(xlsxExcess.body(), StandardCharsets.UTF_8)).contains("REPORT_EXPORT_LIMIT_EXCEEDED");
        var pdfExcess = downloadPdf(owner.token(), "");
        assertThat(pdfExcess.statusCode()).isEqualTo(400);
        assertThat(pdfExcess.headers().firstValue("Content-Disposition")).isEmpty();
        assertThat(new String(pdfExcess.body(), StandardCharsets.UTF_8)).contains("REPORT_EXPORT_LIMIT_EXCEEDED");
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

    @Test
    void shouldDownloadTypedXlsxWithTheSameRowsTotalsAndFiltersAsCsv() throws Exception {
        var owner = user();
        String account = account(owner, "Conta principal", "999.00");
        String income = category(owner, "Salário", "INCOME");
        String expense = category(owner, "Alimentação", "EXPENSE");
        transaction(owner, account, income, "INCOME", "1000", START);
        String expenseId = transaction(owner, account, expense, "EXPENSE", "250.50", END);
        transaction(owner, account, expense, "EXPENSE", "20", START.minusDays(1));
        jdbc.update("UPDATE transactions SET description = ? WHERE id = ?", "=1+1", UUID.fromString(expenseId));
        var other = user();
        transaction(other, account(other, "Conta privada", "0"), category(other, "Privada", "INCOME"), "INCOME", "999", START);

        var response = downloadXlsx(owner.token(), "");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValue("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(response.headers().firstValue("Content-Disposition"))
                .hasValue("attachment; filename=\"finance-manager_2025-01-01_2025-01-31.xlsx\"");
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(response.body()))) {
            var summary = workbook.getSheet("Resumo");
            assertThat(summary.getRow(10).getCell(1).getNumericCellValue()).isEqualTo(2);
            assertThat(summary.getRow(11).getCell(1).getNumericCellValue()).isEqualTo(1000);
            assertThat(summary.getRow(12).getCell(1).getNumericCellValue()).isEqualTo(250.5);
            assertThat(summary.getRow(13).getCell(1).getNumericCellValue()).isEqualTo(749.5);
            var details = workbook.getSheet("Transações");
            assertThat(details.getLastRowNum()).isEqualTo(2);
            assertThat(DateUtil.isCellDateFormatted(details.getRow(1).getCell(0))).isTrue();
            assertThat(details.getRow(1).getCell(0).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(START);
            assertThat(details.getRow(2).getCell(0).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(END);
            assertThat(details.getRow(2).getCell(1).getCellType()).isEqualTo(CellType.STRING);
            assertThat(details.getRow(2).getCell(1).getStringCellValue()).isEqualTo("=1+1");
            assertThat(details.getRow(2).getCell(3).getStringCellValue()).isEqualTo("Conta principal");
            assertThat(details.getRow(2).getCell(4).getStringCellValue()).isEqualTo("Alimentação");
            assertThat(details.getRow(2).getCell(5).getNumericCellValue()).isEqualTo(250.5);
        }
        assertThat(download(owner.token(), PATH).body()).isEqualTo(HEADER
                + "2025-01-01;;Receita;Conta principal;Salário;1000,00\r\n"
                + "2025-01-31;'=1+1;Despesa;Conta principal;Alimentação;250,50\r\n");
        var filtered = downloadXlsx(owner.token(), "&type=EXPENSE&accountId=" + account + "&categoryId=" + expense);
        assertThat(filtered.statusCode()).isEqualTo(200);
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(filtered.body()))) {
            var summary = workbook.getSheet("Resumo");
            assertThat(summary.getRow(4).getCell(1).getStringCellValue()).isEqualTo("Despesa");
            assertThat(summary.getRow(5).getCell(1).getStringCellValue()).isEqualTo("Conta principal");
            assertThat(summary.getRow(6).getCell(1).getStringCellValue()).isEqualTo("Alimentação");
            assertThat(summary.getRow(11).getCell(1).getNumericCellValue()).isZero();
            assertThat(summary.getRow(13).getCell(1).getNumericCellValue()).isEqualTo(-250.5);
            assertThat(workbook.getSheet("Transações").getLastRowNum()).isEqualTo(1);
        }
    }

    @Test
    void shouldKeepSelectedNamesInEmptyXlsx() throws Exception {
        var owner = user();
        String account = account(owner, "@Conta", "0");
        String category = category(owner, "=Categoria", "INCOME");
        var response = downloadXlsx(owner.token(), "&accountId=" + account + "&categoryId=" + category);
        assertThat(response.statusCode()).isEqualTo(200);
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(response.body()))) {
            assertThat(workbook.getSheet("Resumo").getRow(5).getCell(1).getStringCellValue()).isEqualTo("@Conta");
            assertThat(workbook.getSheet("Resumo").getRow(6).getCell(1).getStringCellValue()).isEqualTo("=Categoria");
            assertThat(workbook.getSheet("Resumo").getRow(13).getCell(1).getNumericCellValue()).isZero();
            assertThat(workbook.getSheet("Resumo").getRow(15).getCell(1).getStringCellValue()).contains("Sem movimentações");
            assertThat(workbook.getSheet("Transações").getPhysicalNumberOfRows()).isEqualTo(1);
        }
    }

    @Test
    void shouldRejectXlsxPrecisionLossWithoutAttachmentAndKeepCsvAvailable() throws Exception {
        var owner = user();
        transaction(owner, account(owner, "Conta", "0"), category(owner, "Salário", "INCOME"),
                "INCOME", "10000000000000.01", START);
        var response = downloadXlsx(owner.token(), "");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).contains("application/problem+json");
        assertThat(response.headers().firstValue("Content-Disposition")).isEmpty();
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).contains("XLSX_PRECISION_EXCEEDED");
        var csv = download(owner.token(), PATH);
        assertThat(csv.statusCode()).isEqualTo(200);
        assertThat(csv.body()).contains("10000000000000,01");
        var pdf = downloadPdf(owner.token(), "");
        assertThat(pdf.statusCode()).isEqualTo(200);
        try (var reader = new PdfReader(pdf.body())) {
            assertThat(new PdfTextExtractor(reader).getTextFromPage(1)).contains("R$ 10.000.000.000.000,01");
        }
    }

    @Test
    void shouldDownloadPdfWithSummaryFiltersAndOnlyOwnedMovements() throws Exception {
        var owner = user();
        String account = account(owner, "Conta principal", "999");
        String income = category(owner, "Salário", "INCOME");
        String expense = category(owner, "Alimentação", "EXPENSE");
        String first = transaction(owner, account, income, "INCOME", "1000", START);
        String last = transaction(owner, account, expense, "EXPENSE", "250.50", END);
        jdbc.update("UPDATE transactions SET description = 'PUBLICAINCOME' WHERE id = ?", UUID.fromString(first));
        jdbc.update("UPDATE transactions SET description = 'PUBLICAEXPENSE' WHERE id = ?", UUID.fromString(last));
        transaction(owner, account, expense, "EXPENSE", "9999", START.minusDays(1));
        var other = user();
        transaction(other, account(other, "Conta privada", "0"), category(other, "Privada", "INCOME"), "INCOME", "12345", START);
        var response = downloadPdf(owner.token(), "");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/pdf");
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(response.headers().firstValue("Content-Disposition"))
                .hasValue("attachment; filename=\"finance-manager_2025-01-01_2025-01-31.pdf\"");
        try (var reader = new PdfReader(response.body())) {
            assertThat(new PdfTextExtractor(reader).getTextFromPage(1)).contains("01/01/2025", "31/01/2025",
                    "R$ 1.000,00", "R$ 250,50", "R$ 749,50", "PUBLICAINCOME", "PUBLICAEXPENSE", "Página 1 de 1")
                    .doesNotContain("Conta privada", "Privada", "12.345,00", "9.999,00");
        }
        var filtered = downloadPdf(owner.token(), "&type=EXPENSE&accountId=" + account + "&categoryId=" + expense);
        assertThat(filtered.statusCode()).isEqualTo(200);
        try (var reader = new PdfReader(filtered.body())) {
            assertThat(new PdfTextExtractor(reader).getTextFromPage(1)).contains("Tipo: Despesa", "Conta: Conta principal",
                    "Categoria: Alimentação", "R$ 0,00", "-R$ 250,50", "PUBLICAEXPENSE").doesNotContain("PUBLICAINCOME");
        }
    }

    @Test
    void shouldPreserveSelectedNamesInEmptyPdf() throws Exception {
        var owner = user();
        String account = account(owner, "Conta selecionada", "0");
        String category = category(owner, "Alimentação", "EXPENSE");
        var response = downloadPdf(owner.token(), "&accountId=" + account + "&categoryId=" + category);
        assertThat(response.statusCode()).isEqualTo(200);
        try (var reader = new PdfReader(response.body())) {
            assertThat(new PdfTextExtractor(reader).getTextFromPage(1)).contains("Conta: Conta selecionada",
                    "Categoria: Alimentação", "R$ 0,00", "Sem movimentações no período.");
        }
    }

    private HttpResponse<byte[]> downloadPdf(String token, String filters) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + exportPort
                        + PATH.replace("format=CSV", "format=PDF") + filters))
                .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + token)
                .header("Accept", "application/pdf").GET().build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<byte[]> downloadXlsx(String token, String filters) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + exportPort
                        + PATH.replace("format=CSV", "format=XLSX") + filters))
                .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").GET().build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private HttpResponse<String> download(String token, String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + exportPort + path))
                .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + token)
                .header("Accept", "text/csv").GET().build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
