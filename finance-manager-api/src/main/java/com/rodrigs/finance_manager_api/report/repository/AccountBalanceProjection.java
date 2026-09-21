package com.rodrigs.finance_manager_api.report.repository;

import com.rodrigs.finance_manager_api.financial_account.enums.AccountType;

import java.math.BigDecimal;
import java.util.UUID;

public interface AccountBalanceProjection {
    UUID getAccountId();
    String getAccountName();
    AccountType getAccountType();
    BigDecimal getBalance();
}
