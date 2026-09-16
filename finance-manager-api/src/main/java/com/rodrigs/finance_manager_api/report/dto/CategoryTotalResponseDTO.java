package com.rodrigs.finance_manager_api.report.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CategoryTotalResponseDTO(UUID categoryId, String categoryName, BigDecimal amount) {
}
