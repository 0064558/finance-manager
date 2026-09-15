package com.rodrigs.finance_manager_api.report.repository;

import java.math.BigDecimal;
import java.util.UUID;

public interface CategoryExpenseProjection {
    UUID getCategoryId();
    String getCategoryName();
    BigDecimal getTotalExpense();
}
