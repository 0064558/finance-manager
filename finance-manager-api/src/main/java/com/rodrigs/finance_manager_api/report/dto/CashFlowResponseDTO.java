package com.rodrigs.finance_manager_api.report.dto;

import java.time.LocalDate;
import java.util.List;

public record CashFlowResponseDTO(
        LocalDate startDate,
        LocalDate endDate,
        List<CashFlowPointResponseDTO> points
) {
}
