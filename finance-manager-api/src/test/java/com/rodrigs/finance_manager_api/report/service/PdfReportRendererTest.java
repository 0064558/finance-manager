package com.rodrigs.finance_manager_api.report.service;

import com.rodrigs.finance_manager_api.report.dto.ReportExportData;
import com.rodrigs.finance_manager_api.report.dto.ReportExportFilter;
import com.rodrigs.finance_manager_api.report.dto.ReportExportRow;
import com.rodrigs.finance_manager_api.shared.enums.TransactionType;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.assertThat;

class PdfReportRendererTest {
    private static final LocalDate DATE = LocalDate.of(2025, 1, 1);
    private static final ReportExportFilter FILTER = new ReportExportFilter(DATE, DATE.plusDays(30), null, null, null);
    private final PdfReportRenderer renderer = new PdfReportRenderer(
            Clock.fixed(Instant.parse("2026-10-30T18:00:00Z"), ZoneOffset.UTC));

    @Test
    void shouldWriteLandscapePdfWithAccentsDatesExactMoneyAndTotals() throws Exception {
        var rows = List.of(row("Salário", TransactionType.INCOME, "1000.00"),
                row("Café; \"especial\"", TransactionType.EXPENSE, "250.50"));
        byte[] bytes = renderer.render(new ReportExportData(FILTER, rows));
        assertThat(bytes).startsWith((byte) '%', (byte) 'P', (byte) 'D', (byte) 'F');
        try (var reader = new PdfReader(bytes)) {
            assertThat(reader.getNumberOfPages()).isEqualTo(1);
            assertThat(reader.getPageSize(1).getWidth()).isCloseTo(842, org.assertj.core.data.Offset.offset(1f));
            assertThat(reader.getPageSize(1).getHeight()).isCloseTo(595, org.assertj.core.data.Offset.offset(1f));
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(text).contains("DinDim / Finance Manager", "Relatório de movimentações", "01/01/2025",
                    "31/01/2025", "30/10/2026 18:00:00 UTC", "Receitas", "Despesas", "Saldo líquido",
                    "R$ 1.000,00", "R$ 250,50", "R$ 749,50", "Café; \"especial\"", "Página 1 de 1");
        }
    }

    @Test
    void shouldPreserveNamesAndZeroTotalsForEmptyFilteredReport() throws Exception {
        var filter = new ReportExportFilter(DATE, DATE, TransactionType.EXPENSE, UUID.randomUUID(), UUID.randomUUID());
        byte[] bytes = renderer.render(new ReportExportData(filter, List.of(), "Conta selecionada", "Alimentação"));
        preview("empty", bytes);
        try (var reader = new PdfReader(bytes)) {
            assertThat(reader.getNumberOfPages()).isEqualTo(1);
            assertThat(new PdfTextExtractor(reader).getTextFromPage(1)).contains("Tipo: Despesa",
                    "Conta: Conta selecionada", "Categoria: Alimentação", "R$ 0,00", "Sem movimentações no período.", "Página 1 de 1");
        }
    }

    @Test
    void shouldRepeatHeadersNumberEveryPageAndPreserveEveryRow() throws Exception {
        var rows = IntStream.range(0, 100).mapToObj(i -> row("MOV%03d Café, alimentação e transporte.\nDescrição complementar.".formatted(i),
                i % 2 == 0 ? TransactionType.INCOME : TransactionType.EXPENSE, "10.50")).toList();
        byte[] bytes = renderer.render(new ReportExportData(FILTER, rows));
        preview("multipage", bytes);
        try (var reader = new PdfReader(bytes)) {
            assertThat(reader.getNumberOfPages()).isGreaterThan(1);
            var text = new StringBuilder();
            var extractor = new PdfTextExtractor(reader);
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                String pageText = extractor.getTextFromPage(page);
                assertThat(pageText).contains("Data", "Descrição", "Tipo", "Conta", "Categoria", "Valor",
                        "Página " + page + " de " + reader.getNumberOfPages());
                text.append(pageText);
            }
            for (int i = 0; i < 100; i++) assertThat(text).contains("MOV%03d".formatted(i));
            assertThat(text.indexOf("MOV000")).isLessThan(text.indexOf("MOV099"));
        }
    }

    @Test
    void shouldSplitOversizedRowsAndLongWordsWithoutLosingText() throws Exception {
        String multiline = "INÍCIO\n" + "x\n".repeat(110) + "FIM-DESCRIÇÃO";
        String longWord = "acentuação".repeat(25);
        var rows = List.of(row(multiline, TransactionType.EXPENSE, "10000000000000.01"),
                row(longWord, TransactionType.EXPENSE, "0.99"));
        byte[] bytes = renderer.render(new ReportExportData(FILTER, rows));
        preview("stress", bytes);
        try (var reader = new PdfReader(bytes)) {
            assertThat(reader.getNumberOfPages()).isGreaterThan(1);
            var text = new StringBuilder();
            var extractor = new PdfTextExtractor(reader);
            for (int page = 1; page <= reader.getNumberOfPages(); page++) text.append(extractor.getTextFromPage(page));
            assertThat(text).contains("INÍCIO", "FIM-DESCRIÇÃO", "-R$ 10.000.000.000.001,00", "R$ 10.000.000.000.000,01");
            assertThat(text.toString().replaceAll("\\s", "")).contains(longWord);
        }
    }

    @Test
    void shouldRenderLiteralTextAndNullDescriptionsWithoutInterpretingMarkup() throws Exception {
        byte[] bytes = renderer.render(new ReportExportData(FILTER, List.of(
                row("<b>Texto literal</b> =1+1", TransactionType.INCOME, "0.01"),
                row(null, TransactionType.EXPENSE, "0.02"))));
        try (var reader = new PdfReader(bytes)) {
            assertThat(new PdfTextExtractor(reader).getTextFromPage(1)).contains("<b>Texto literal</b> =1+1", "-R$ 0,01");
        }
    }

    @Test
    void shouldInclude5000RowsWithoutTruncation() throws Exception {
        var rows = IntStream.range(0, 5000).mapToObj(i -> row("L%04d".formatted(i), TransactionType.INCOME, "0.01")).toList();
        try (var reader = new PdfReader(renderer.render(new ReportExportData(FILTER, rows)))) {
            var text = new StringBuilder();
            var extractor = new PdfTextExtractor(reader);
            for (int page = 1; page <= reader.getNumberOfPages(); page++) text.append(extractor.getTextFromPage(page));
            for (int i = 0; i < 5000; i++) assertThat(text).contains("L%04d".formatted(i));
        }
    }

    private ReportExportRow row(String description, TransactionType type, String amount) {
        return new ReportExportRow(DATE, description, type, "Conta principal", "Alimentação", new BigDecimal(amount));
    }

    private void preview(String name, byte[] bytes) throws Exception {
        if (Boolean.getBoolean("pdf.preview")) {
            Path directory = Path.of("target", "pdf-qa");
            Files.createDirectories(directory);
            Files.write(directory.resolve(name + ".pdf"), bytes);
        }
    }
}
