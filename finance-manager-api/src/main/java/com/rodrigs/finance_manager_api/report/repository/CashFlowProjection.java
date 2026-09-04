package com.rodrigs.finance_manager_api.report.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface CashFlowProjection {
    LocalDate getOccurredOn();

    BigDecimal getTotalIncome();

    BigDecimal getTotalExpense();
}
