package com.rodrigs.finance_manager_api.report.repository;

import java.math.BigDecimal;
import java.util.UUID;

public interface AccountBalanceProjection {
    UUID getAccountId();
    String getAccountName();
    BigDecimal getBalance();
}
