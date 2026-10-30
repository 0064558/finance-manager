package com.rodrigs.finance_manager_api.report.service;

import com.rodrigs.finance_manager_api.report.dto.ReportExportData;
import com.rodrigs.finance_manager_api.shared.enums.TransactionType;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;

@Component
public class CsvReportRenderer {
    public byte[] render(ReportExportData data) {
        var csv = new StringBuilder("\uFEFFData;Descrição;Tipo;Conta;Categoria;Valor\r\n");
        for (var row : data.rows()) {
            csv.append(row.date()).append(';')
                    .append(text(row.description())).append(';')
                    .append(row.type() == TransactionType.INCOME ? "Receita" : "Despesa").append(';')
                    .append(text(row.accountName())).append(';')
                    .append(text(row.categoryName())).append(';')
                    .append(row.amount().setScale(2).toPlainString().replace('.', ',')).append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String text(String value) {
        if (value == null) return "";
        // Quoting alone does not stop spreadsheets from interpreting formulas.
        boolean dangerous = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\t' || c == '\r' || c == '\n' || c == '=' || c == '+' || c == '-' || c == '@') {
                dangerous = true;
                break;
            }
            if (!Character.isWhitespace(c) && !Character.isSpaceChar(c)) break;
        }
        String safe = dangerous ? "'" + value : value;
        if (safe.indexOf(';') >= 0 || safe.indexOf('"') >= 0 || safe.indexOf('\r') >= 0 || safe.indexOf('\n') >= 0) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }
}
