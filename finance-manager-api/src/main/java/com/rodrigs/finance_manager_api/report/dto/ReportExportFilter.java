package com.rodrigs.finance_manager_api.report.dto;

import com.rodrigs.finance_manager_api.shared.enums.TransactionType;
import java.time.LocalDate;
import java.util.UUID;

public record ReportExportFilter(LocalDate startDate, LocalDate endDate, TransactionType type,
                                 UUID accountId, UUID categoryId) {
}
