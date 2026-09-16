package com.rodrigs.finance_manager_api.report.repository;

import java.math.BigDecimal;
import java.util.UUID;

public interface CategoryTotalProjection {
    UUID getCategoryId();
    String getCategoryName();
    BigDecimal getAmount();
}
