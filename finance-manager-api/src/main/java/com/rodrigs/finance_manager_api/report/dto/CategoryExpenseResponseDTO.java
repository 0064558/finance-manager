package com.rodrigs.finance_manager_api.report.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CategoryExpenseResponseDTO(UUID categoryId, String categoryName, BigDecimal totalExpense) {
}
