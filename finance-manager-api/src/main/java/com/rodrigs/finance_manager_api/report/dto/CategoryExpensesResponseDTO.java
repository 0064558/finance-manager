package com.rodrigs.finance_manager_api.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CategoryExpensesResponseDTO(
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal totalExpense,
        List<CategoryExpenseResponseDTO> categories
) {
}
