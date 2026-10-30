package com.rodrigs.finance_manager_api.report.service;

import com.rodrigs.finance_manager_api.report.dto.ReportExportData;
import com.rodrigs.finance_manager_api.shared.enums.TransactionType;
import com.rodrigs.finance_manager_api.shared.exception.XlsxPrecisionExceededException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class XlsxReportRenderer {
    private static final MathContext EXCEL_PRECISION = new MathContext(15);
    private final Clock clock;

    public XlsxReportRenderer() {
        this(Clock.systemUTC());
    }

    XlsxReportRenderer(Clock clock) {
        this.clock = clock;
    }

    public byte[] render(ReportExportData data) throws IOException {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var styles = styles(workbook);
            summary(workbook.createSheet("Resumo"), data, styles);
            transactions(workbook.createSheet("Transações"), data, styles);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private void summary(Sheet sheet, ReportExportData data, Styles styles) {
        sheet.setColumnWidth(0, 30 * 256);
        sheet.setColumnWidth(1, 65 * 256);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));
        var title = sheet.createRow(0);
        title.setHeightInPoints(28);
        title.createCell(0).setCellValue("DinDim / Finance Manager — Relatório de movimentações");
        title.getCell(0).setCellStyle(styles.header());
        title.createCell(1).setCellStyle(styles.header());
        var filter = data.filter();
        date(value(sheet, 2, "Data inicial", styles), filter.startDate(), styles);
        date(value(sheet, 3, "Data final", styles), filter.endDate(), styles);
        value(sheet, 4, "Tipo", styles).setCellValue(filter.type() == null ? "Todos" : typeName(filter.type()));
        value(sheet, 5, "Conta", styles).setCellValue(data.accountName() == null ? "Todas as contas" : data.accountName());
        value(sheet, 6, "Categoria", styles).setCellValue(data.categoryName() == null ? "Todas as categorias" : data.categoryName());
        value(sheet, 7, "Gerado em (UTC)", styles).setCellValue(
                OffsetDateTime.now(clock).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        value(sheet, 8, "Moeda", styles).setCellValue("BRL (Real brasileiro)");
        value(sheet, 10, "Quantidade de transações", styles).setCellValue(data.rows().size());
        money(value(sheet, 11, "Receitas", styles), data.totalIncome(), styles);
        money(value(sheet, 12, "Despesas", styles), data.totalExpense(), styles);
        money(value(sheet, 13, "Saldo líquido", styles), data.netBalance(), styles);
        value(sheet, 15, "Situação", styles).setCellValue(data.rows().isEmpty()
                ? "Sem movimentações no período." : "Todas as movimentações do recorte estão na aba Transações.");
        value(sheet, 16, "Sobre o saldo líquido", styles).setCellValue("Receitas menos despesas; não inclui saldos iniciais das contas.");
    }

    private void transactions(Sheet sheet, ReportExportData data, Styles styles) {
        String[] headers = {"Data", "Descrição", "Tipo", "Conta", "Categoria", "Valor"};
        int[] widths = {14, 58, 14, 28, 28, 24};
        var header = sheet.createRow(0);
        header.setHeightInPoints(24);
        for (int i = 0; i < headers.length; i++) {
            header.createCell(i).setCellValue(headers[i]);
            header.getCell(i).setCellStyle(styles.header());
            sheet.setColumnWidth(i, widths[i] * 256);
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, data.rows().size(), 0, headers.length - 1));
        int index = 1;
        for (var item : data.rows()) {
            var row = sheet.createRow(index++);
            row.setHeightInPoints(32);
            date(row.createCell(0), item.date(), styles);
            // setCellValue(String) preserves text such as '=1+1' without creating formulas.
            row.createCell(1).setCellValue(item.description() == null ? "" : item.description());
            row.getCell(1).setCellStyle(styles.text());
            row.createCell(2).setCellValue(typeName(item.type()));
            row.createCell(3).setCellValue(item.accountName());
            row.createCell(4).setCellValue(item.categoryName());
            money(row.createCell(5), item.amount(), styles);
        }
    }

    private Cell value(Sheet sheet, int index, String label, Styles styles) {
        var row = sheet.createRow(index);
        row.setHeightInPoints(30);
        row.createCell(0).setCellValue(label);
        row.getCell(0).setCellStyle(styles.label());
        var cell = row.createCell(1);
        cell.setCellStyle(styles.text());
        return cell;
    }

    private void date(Cell cell, java.time.LocalDate date, Styles styles) {
        cell.setCellValue(date.atStartOfDay());
        cell.setCellStyle(styles.date());
    }

    private void money(Cell cell, BigDecimal amount, Styles styles) {
        double number = amount.doubleValue();
        // Excel only retains 15 significant digits. Never silently discard cents.
        var restored = BigDecimal.valueOf(number).round(EXCEL_PRECISION).setScale(2, RoundingMode.HALF_UP);
        if (restored.compareTo(amount) != 0) throw new XlsxPrecisionExceededException();
        cell.setCellValue(number);
        cell.setCellStyle(styles.money());
    }

    private String typeName(TransactionType type) {
        return type == TransactionType.INCOME ? "Receita" : "Despesa";
    }

    private Styles styles(Workbook workbook) {
        var bold = workbook.createFont();
        bold.setBold(true);
        var white = workbook.createFont();
        white.setBold(true);
        white.setColor(IndexedColors.WHITE.getIndex());
        var header = workbook.createCellStyle();
        header.setFont(white);
        header.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        var label = workbook.createCellStyle();
        label.setFont(bold);
        var date = workbook.createCellStyle();
        date.setDataFormat(workbook.createDataFormat().getFormat("dd/mm/yyyy"));
        var money = workbook.createCellStyle();
        money.setDataFormat(workbook.createDataFormat().getFormat("\"R$\" #,##0.00;[Red]-\"R$\" #,##0.00"));
        var text = workbook.createCellStyle();
        text.setWrapText(true);
        return new Styles(header, label, date, money, text);
    }

    private record Styles(CellStyle header, CellStyle label, CellStyle date, CellStyle money, CellStyle text) {}
}
