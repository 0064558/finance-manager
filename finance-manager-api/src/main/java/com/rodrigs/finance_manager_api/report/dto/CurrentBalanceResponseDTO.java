package com.rodrigs.finance_manager_api.report.dto;

import java.math.BigDecimal;
import java.util.List;

public record CurrentBalanceResponseDTO(
        BigDecimal totalBalance,
        List<AccountBalanceResponseDTO> accounts
) {
}
