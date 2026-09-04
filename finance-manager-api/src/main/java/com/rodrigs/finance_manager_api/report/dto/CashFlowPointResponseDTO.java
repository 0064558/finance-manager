package com.rodrigs.finance_manager_api.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CashFlowPointResponseDTO(
        LocalDate date,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal netBalance
) {
}
