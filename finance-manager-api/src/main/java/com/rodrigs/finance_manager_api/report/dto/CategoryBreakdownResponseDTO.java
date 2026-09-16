package com.rodrigs.finance_manager_api.report.dto;

import com.rodrigs.finance_manager_api.shared.enums.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CategoryBreakdownResponseDTO(
        LocalDate startDate, LocalDate endDate, TransactionType type,
        BigDecimal totalAmount, List<CategoryTotalResponseDTO> categories
) {
}
