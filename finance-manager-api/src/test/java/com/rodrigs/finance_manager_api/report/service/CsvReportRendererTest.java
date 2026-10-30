package com.rodrigs.finance_manager_api.report.service;

import com.rodrigs.finance_manager_api.report.dto.ReportExportData;
import com.rodrigs.finance_manager_api.report.dto.ReportExportFilter;
import com.rodrigs.finance_manager_api.report.dto.ReportExportRow;
import com.rodrigs.finance_manager_api.shared.enums.TransactionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class CsvReportRendererTest {
    private static final LocalDate DATE = LocalDate.of(2026, 10, 1);
    private static final ReportExportFilter FILTER = new ReportExportFilter(DATE, DATE, null, null, null);
    private final CsvReportRenderer renderer = new CsvReportRenderer();

    @Test
    void shouldWriteBomAccentsDecimalsAndEscapedMultilineText() {
        var row = new ReportExportRow(DATE, "Café; \"especial\"\nsegunda linha", TransactionType.EXPENSE,
                "Conta; principal", "Alimentação", new BigDecimal("1250.50"));
        byte[] bytes = renderer.render(new ReportExportData(FILTER, List.of(row)));
        assertThat(bytes).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo(
                "\uFEFFData;Descrição;Tipo;Conta;Categoria;Valor\r\n"
                + "2026-10-01;\"Café; \"\"especial\"\"\nsegunda linha\";Despesa;\"Conta; principal\";Alimentação;1250,50\r\n");
    }

    @ParameterizedTest
    @ValueSource(strings = {"=1+1", "+1+1", "-1+1", "@SUM(A1)", "  =1+1", "\t=1+1", "\r=1", "\n=1", "\u00A0=1"})
    void shouldNeutralizeFormulasInEveryUserControlledColumn(String value) {
        var row = new ReportExportRow(DATE, value, TransactionType.INCOME, value, value, BigDecimal.ONE);
        String csv = new String(renderer.render(new ReportExportData(FILTER, List.of(row))), StandardCharsets.UTF_8);
        assertThat(csv).contains("'" + value);
        assertThat(csv.chars().filter(c -> c == '\'').count()).isEqualTo(3);
    }

    @Test
    void shouldWriteOnlyHeaderForEmptyDataAndEmptyCellForNullDescription() {
        String header = "\uFEFFData;Descrição;Tipo;Conta;Categoria;Valor\r\n";
        assertThat(new String(renderer.render(new ReportExportData(FILTER, List.of())), StandardCharsets.UTF_8))
                .isEqualTo(header);
        var row = new ReportExportRow(DATE, null, TransactionType.INCOME, "Conta", "Salário", BigDecimal.ONE);
        assertThat(new String(renderer.render(new ReportExportData(FILTER, List.of(row))), StandardCharsets.UTF_8))
                .isEqualTo(header + "2026-10-01;;Receita;Conta;Salário;1,00\r\n");
    }

    @Test
    void shouldComputeExactTotalsFromTheSameImmutableRows() {
        var income = new ReportExportRow(DATE, null, TransactionType.INCOME, "Conta", "Salário", new BigDecimal("1000.00"));
        var expense = new ReportExportRow(DATE, null, TransactionType.EXPENSE, "Conta", "Alimentação", new BigDecimal("250.50"));
        var data = new ReportExportData(FILTER, List.of(income, expense));
        assertThat(data.totalIncome()).isEqualByComparingTo("1000.00");
        assertThat(data.totalExpense()).isEqualByComparingTo("250.50");
        assertThat(data.netBalance()).isEqualByComparingTo("749.50");
        assertThat(new ReportExportData(FILTER, List.of(expense)).netBalance()).isEqualByComparingTo("-250.50");
        assertThat(new ReportExportData(FILTER, List.of()).netBalance()).isEqualByComparingTo("0.00");
    }
}
