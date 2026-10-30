package com.rodrigs.finance_manager_api.report.dto;

import com.rodrigs.finance_manager_api.shared.enums.TransactionType;
import java.math.BigDecimal;
import java.util.List;

/** Detached snapshot shared by all export formats. Totals always come from the exported rows. */
public record ReportExportData(ReportExportFilter filter, List<ReportExportRow> rows) {
    public ReportExportData {
        rows = List.copyOf(rows);
    }

    public BigDecimal totalIncome() {
        return total(TransactionType.INCOME);
    }

    public BigDecimal totalExpense() {
        return total(TransactionType.EXPENSE);
    }

    public BigDecimal netBalance() {
        return totalIncome().subtract(totalExpense());
    }

    private BigDecimal total(TransactionType type) {
        return rows.stream().filter(row -> row.type() == type).map(ReportExportRow::amount)
                .reduce(new BigDecimal("0.00"), BigDecimal::add);
    }
}
