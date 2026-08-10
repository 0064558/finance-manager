package com.rodrigs.finance_manager_api.shared.exception;

public class FinancialAccountNotFoundException extends RuntimeException {
    public FinancialAccountNotFoundException(String message) {
        super("Financial account not found.");
    }
}
