package com.rodrigs.finance_manager_api.shared.exception;

public class FinancialAccountNotFoundException extends RuntimeException {
    public FinancialAccountNotFoundException() {
        super("Account not found for the authenticated user.");
    }
}
