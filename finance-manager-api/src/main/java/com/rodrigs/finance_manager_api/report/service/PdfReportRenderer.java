package com.rodrigs.finance_manager_api.report.service;

import com.rodrigs.finance_manager_api.report.dto.ReportExportData;
import com.rodrigs.finance_manager_api.shared.enums.TransactionType;
import org.librepdf.openpdf.fonts.Liberation;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.ColumnText;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfStamper;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class PdfReportRenderer {
    private static final Color TEAL = new Color(0, 85, 85);
    private static final Color PALE = new Color(238, 246, 246);
    private static final Color MUTED = new Color(80, 95, 95);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final Clock clock;

    public PdfReportRenderer() {
        this(Clock.systemUTC());
    }

    PdfReportRenderer(Clock clock) {
        this.clock = clock;
    }

    public byte[] render(ReportExportData data) throws IOException {
        var fonts = fonts();
        try (var output = new ByteArrayOutputStream()) {
            var document = new Document(new Rectangle(PageSize.A4.getHeight(), PageSize.A4.getWidth()), 36, 36, 32, 42);
            PdfWriter.getInstance(document, output);
            document.addTitle("Finance Manager - Relatório de movimentações");
            document.addCreator("Finance Manager");
            try {
                document.open();
                heading(document, data, fonts);
                summary(document, data, fonts);
                transactions(document, data, fonts);
            } finally {
                document.close();
            }
            return numberPages(output.toByteArray(), fonts);
        }
    }

    private void heading(Document document, ReportExportData data, Fonts fonts) {
        var title = new Paragraph("DinDim / Finance Manager", fonts.title());
        title.setSpacingAfter(5);
        document.add(title);
        var period = new Paragraph("Relatório de movimentações | " + DATE.format(data.filter().startDate())
                + " a " + DATE.format(data.filter().endDate()), fonts.bold());
        period.setSpacingAfter(9);
        document.add(period);
        var filters = new PdfPTable(1);
        filters.setWidthPercentage(100);
        filters.setSpacingAfter(12);
        var cell = cell("Tipo: " + (data.filter().type() == null ? "Todos" : typeName(data.filter().type()))
                + "\nConta: " + (data.accountName() == null ? "Todas as contas" : data.accountName())
                + "\nCategoria: " + (data.categoryName() == null ? "Todas as categorias" : data.categoryName())
                + "\nGerado em: " + OffsetDateTime.now(clock).format(
                        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss 'UTC'")), fonts.body(), Color.WHITE);
        cell.setBorder(Rectangle.BOTTOM);
        filters.addCell(cell);
        document.add(filters);
    }

    private void summary(Document document, ReportExportData data, Fonts fonts) {
        var table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setSpacingAfter(5);
        String[] labels = {"Movimentações", "Receitas", "Despesas", "Saldo líquido"};
        String[] values = {Integer.toString(data.rows().size()), money(data.totalIncome()),
                money(data.totalExpense()), money(data.netBalance())};
        for (int i = 0; i < labels.length; i++) {
            var cell = new PdfPCell();
            cell.setPadding(10);
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setBackgroundColor(PALE);
            cell.addElement(new Paragraph(labels[i], fonts.muted()));
            cell.addElement(new Paragraph(values[i], fonts.bold()));
            table.addCell(cell);
        }
        document.add(table);
        var note = new Paragraph("Saldo líquido = receitas menos despesas; não inclui saldos iniciais das contas. Valores em BRL.", fonts.muted());
        note.setSpacingAfter(12);
        document.add(note);
    }

    private void transactions(Document document, ReportExportData data, Fonts fonts) {
        var table = new PdfPTable(new float[]{9, 30, 10, 16, 16, 19});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        table.setSplitRows(true);
        table.setSplitLate(false);
        String[] headings = {"Data", "Descrição", "Tipo", "Conta", "Categoria", "Valor"};
        for (var heading : headings) table.addCell(cell(heading, fonts.header(), TEAL));
        int index = 0;
        for (var row : data.rows()) {
            Color background = index++ % 2 == 0 ? Color.WHITE : PALE;
            table.addCell(cell(DATE.format(row.date()), fonts.body(), background));
            table.addCell(cell(row.description() == null ? "" : row.description(), fonts.body(), background));
            table.addCell(cell(typeName(row.type()), fonts.body(), background));
            table.addCell(cell(row.accountName(), fonts.body(), background));
            table.addCell(cell(row.categoryName(), fonts.body(), background));
            var amount = cell(money(row.amount()), fonts.body(), background);
            amount.setHorizontalAlignment(Element.ALIGN_RIGHT);
            table.addCell(amount);
        }
        if (data.rows().isEmpty()) {
            var empty = cell("Sem movimentações no período.", fonts.body(), Color.WHITE);
            empty.setColspan(6);
            empty.setPadding(18);
            table.addCell(empty);
        }
        document.add(table);
    }

    private PdfPCell cell(String text, Font font, Color background) {
        // Phrase treats user text literally; no HTML parser or link creation is involved.
        var cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(7);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(new Color(215, 225, 225));
        cell.setBackgroundColor(background);
        cell.setVerticalAlignment(Element.ALIGN_TOP);
        return cell;
    }

    private byte[] numberPages(byte[] bytes, Fonts fonts) throws IOException {
        try (var reader = new PdfReader(bytes); var output = new ByteArrayOutputStream()) {
            var stamper = new PdfStamper(reader, output);
            try {
                for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                    var content = stamper.getOverContent(page);
                    ColumnText.showTextAligned(content, Element.ALIGN_LEFT,
                            new Phrase("DinDim / Finance Manager", fonts.muted()), 36, 20, 0);
                    ColumnText.showTextAligned(content, Element.ALIGN_RIGHT,
                            new Phrase("Página " + page + " de " + reader.getNumberOfPages(), fonts.muted()),
                            reader.getPageSizeWithRotation(page).getWidth() - 36, 20, 0);
                }
            } finally {
                stamper.close();
            }
            return output.toByteArray();
        }
    }

    private String money(BigDecimal value) {
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(value).replace('\u00A0', ' ');
    }

    private String typeName(TransactionType type) {
        return type == TransactionType.INCOME ? "Receita" : "Despesa";
    }

    private Fonts fonts() throws IOException {
        Font title = Liberation.SANS_BOLD.create(20);
        title.setColor(TEAL);
        Font header = Liberation.SANS_BOLD.create(9);
        header.setColor(Color.WHITE);
        Font muted = Liberation.SANS.create(8);
        muted.setColor(MUTED);
        return new Fonts(title, Liberation.SANS.create(9), Liberation.SANS_BOLD.create(11), header, muted);
    }

    private record Fonts(Font title, Font body, Font bold, Font header, Font muted) {}
}
