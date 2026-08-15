package com.rodrigs.finance_manager_api.report.repository;

import java.math.BigDecimal;

public interface ReportTotalsProjection {
    BigDecimal getTotalIncome();

    BigDecimal getTotalExpense();
}
