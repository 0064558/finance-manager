package com.rodrigs.finance_manager_api.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ReportSummaryResponseDTO(
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal netBalance
) {
}
