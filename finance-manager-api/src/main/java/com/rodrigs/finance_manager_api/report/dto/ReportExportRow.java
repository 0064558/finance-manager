package com.rodrigs.finance_manager_api.report.dto;

import com.rodrigs.finance_manager_api.shared.enums.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ReportExportRow(LocalDate date, String description, TransactionType type,
                              String accountName, String categoryName, BigDecimal amount) {
}
