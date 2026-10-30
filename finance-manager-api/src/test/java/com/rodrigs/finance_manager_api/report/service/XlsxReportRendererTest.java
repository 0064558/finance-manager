package com.rodrigs.finance_manager_api.report.service;

import com.rodrigs.finance_manager_api.report.dto.ReportExportData;
import com.rodrigs.finance_manager_api.report.dto.ReportExportFilter;
import com.rodrigs.finance_manager_api.report.dto.ReportExportRow;
import com.rodrigs.finance_manager_api.shared.enums.TransactionType;
import com.rodrigs.finance_manager_api.shared.exception.XlsxPrecisionExceededException;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XlsxReportRendererTest {
    private static final LocalDate DATE = LocalDate.of(2025, 1, 1);
    private static final ReportExportFilter FILTER = new ReportExportFilter(DATE, DATE.plusDays(30), null, null, null);
    private final XlsxReportRenderer renderer = new XlsxReportRenderer(
            Clock.fixed(Instant.parse("2026-10-30T18:00:00Z"), ZoneOffset.UTC));

    @Test
    void shouldPreserveTypedDatesMoneyTotalsAndWorkbookStructure() throws Exception {
        var income = row("Salário", TransactionType.INCOME, "1000.00");
        var expense = row("Café; \"especial\"\nsegunda linha", TransactionType.EXPENSE, "250.50");
        try (var workbook = read(new ReportExportData(FILTER, List.of(income, expense)))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            var summary = workbook.getSheetAt(0);
            assertThat(summary.getSheetName()).isEqualTo("Resumo");
            assertThat(summary.getRow(0).getCell(0).getStringCellValue()).contains("DinDim", "Finance Manager");
            assertThat(summary.getRow(7).getCell(1).getStringCellValue()).isEqualTo("2026-10-30T18:00:00Z");
            assertThat(summary.getRow(8).getCell(1).getStringCellValue()).contains("BRL");
            assertThat(summary.getRow(10).getCell(1).getNumericCellValue()).isEqualTo(2);
            assertThat(summary.getRow(11).getCell(1).getNumericCellValue()).isEqualTo(1000);
            assertThat(summary.getRow(12).getCell(1).getNumericCellValue()).isEqualTo(250.5);
            assertThat(summary.getRow(13).getCell(1).getNumericCellValue()).isEqualTo(749.5);
            var details = workbook.getSheetAt(1);
            assertThat(details.getSheetName()).isEqualTo("Transações");
            assertThat(details.getLastRowNum()).isEqualTo(2);
            assertThat(details.getPaneInformation().isFreezePane()).isTrue();
            assertThat(details.getPaneInformation().getHorizontalSplitPosition()).isEqualTo((short) 1);
            assertThat(details.getCTWorksheet().getAutoFilter().getRef()).isEqualTo("A1:F3");
            var date = details.getRow(1).getCell(0);
            assertThat(date.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(DateUtil.isCellDateFormatted(date)).isTrue();
            assertThat(date.getLocalDateTimeCellValue().toLocalDate()).isEqualTo(DATE);
            assertThat(details.getRow(2).getCell(1).getStringCellValue()).isEqualTo(expense.description());
            assertThat(details.getRow(2).getCell(2).getStringCellValue()).isEqualTo("Despesa");
            assertThat(details.getRow(2).getCell(5).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(details.getRow(2).getCell(5).getNumericCellValue()).isEqualTo(250.5);
            assertThat(details.getRow(2).getCell(5).getCellStyle().getDataFormatString()).contains("0.00", "R$");
            assertNoFormulas(workbook);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"=1+1", "+SUM(A1)", "-1+1", "@SUM(A1)", "  =1", "\t=1", "\n=1"})
    void shouldPreserveFormulaLikeTextWithoutCreatingFormulas(String text) throws Exception {
        var item = new ReportExportRow(DATE, text, TransactionType.INCOME, text, text, BigDecimal.ONE);
        var filter = new ReportExportFilter(DATE, DATE, TransactionType.INCOME, UUID.randomUUID(), UUID.randomUUID());
        try (var workbook = read(new ReportExportData(filter, List.of(item), text, text))) {
            var details = workbook.getSheet("Transações").getRow(1);
            for (int column : new int[]{1, 3, 4}) {
                assertThat(details.getCell(column).getCellType()).isEqualTo(CellType.STRING);
                assertThat(details.getCell(column).getStringCellValue()).isEqualTo(text);
            }
            assertThat(workbook.getSheet("Resumo").getRow(5).getCell(1).getStringCellValue()).isEqualTo(text);
            assertThat(workbook.getSheet("Resumo").getRow(6).getCell(1).getStringCellValue()).isEqualTo(text);
            assertNoFormulas(workbook);
        }
    }

    @Test
    void shouldKeepFiltersAndZeroTotalsWhenEmpty() throws Exception {
        var filter = new ReportExportFilter(DATE, DATE, TransactionType.EXPENSE, UUID.randomUUID(), UUID.randomUUID());
        try (var workbook = read(new ReportExportData(filter, List.of(), "Conta selecionada", "Alimentação"))) {
            var summary = workbook.getSheet("Resumo");
            assertThat(summary.getRow(4).getCell(1).getStringCellValue()).isEqualTo("Despesa");
            assertThat(summary.getRow(5).getCell(1).getStringCellValue()).isEqualTo("Conta selecionada");
            assertThat(summary.getRow(6).getCell(1).getStringCellValue()).isEqualTo("Alimentação");
            for (int row : new int[]{10, 11, 12, 13}) {
                assertThat(summary.getRow(row).getCell(1).getNumericCellValue()).isZero();
            }
            assertThat(summary.getRow(15).getCell(1).getStringCellValue()).isEqualTo("Sem movimentações no período.");
            assertThat(workbook.getSheet("Transações").getPhysicalNumberOfRows()).isEqualTo(1);
            assertThat(workbook.getSheet("Transações").getCTWorksheet().getAutoFilter().getRef()).isEqualTo("A1:F1");
        }
    }

    @Test
    void shouldWriteNegativeNetBalanceAndEmptyDescription() throws Exception {
        try (var workbook = read(new ReportExportData(FILTER, List.of(row(null, TransactionType.EXPENSE, "0.01"))))) {
            assertThat(workbook.getSheet("Resumo").getRow(13).getCell(1).getNumericCellValue()).isEqualTo(-0.01);
            assertThat(workbook.getSheet("Transações").getRow(1).getCell(1).getStringCellValue()).isEmpty();
        }
    }

    @Test
    void shouldWrite5000RowsWithReusedStyles() throws Exception {
        var rows = IntStream.range(0, 5000).mapToObj(i -> row("Movimentação " + i, TransactionType.INCOME, "0.01")).toList();
        try (var workbook = read(new ReportExportData(FILTER, rows))) {
            assertThat(workbook.getSheet("Transações").getLastRowNum()).isEqualTo(5000);
            assertThat(workbook.getSheet("Resumo").getRow(11).getCell(1).getNumericCellValue()).isEqualTo(50);
            assertThat(workbook.getNumCellStyles()).isLessThan(20);
        }
    }

    @Test
    void shouldRejectPrecisionLossInIndividualAmountsAndAggregatedTotals() {
        // Each of these is representable individually; their total would lose cents in Excel.
        var safe = row("Receita", TransactionType.INCOME, "5000000000000.01");
        assertThatThrownBy(() -> renderer.render(new ReportExportData(FILTER, List.of(safe, safe))))
                .isInstanceOf(XlsxPrecisionExceededException.class);
        // Opposite types cancel in the net, but each individual total is still unsafe.
        var unsafeIncome = row("Receita", TransactionType.INCOME, "10000000000000.01");
        var unsafeExpense = row("Despesa", TransactionType.EXPENSE, "10000000000000.01");
        assertThatThrownBy(() -> renderer.render(new ReportExportData(FILTER, List.of(unsafeIncome, unsafeExpense))))
                .isInstanceOf(XlsxPrecisionExceededException.class);
        // Individual unsafe amounts may sum to a safe total; rows must also be checked.
        var complement = row("Complemento", TransactionType.INCOME, "0.99");
        assertThatThrownBy(() -> renderer.render(new ReportExportData(FILTER, List.of(unsafeIncome, complement))))
                .isInstanceOf(XlsxPrecisionExceededException.class);
    }

    @Test
    void shouldPreserveCentsAtTheSafePrecisionBoundary() throws Exception {
        try (var workbook = read(new ReportExportData(FILTER,
                List.of(row(null, TransactionType.INCOME, "9999999999999.99"))))) {
            var cell = workbook.getSheet("Transações").getRow(1).getCell(5);
            assertThat(BigDecimal.valueOf(cell.getNumericCellValue())).isEqualByComparingTo("9999999999999.99");
        }
    }

    private ReportExportRow row(String description, TransactionType type, String amount) {
        return new ReportExportRow(DATE, description, type, "Conta principal", "Categoria", new BigDecimal(amount));
    }

    private XSSFWorkbook read(ReportExportData data) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(renderer.render(data)));
    }

    private void assertNoFormulas(XSSFWorkbook workbook) {
        for (var sheet : workbook) for (var row : sheet) for (var cell : row) {
            assertThat(cell.getCellType()).isNotEqualTo(CellType.FORMULA);
        }
    }
}
