package com.rodrigs.finance_manager_api.shared.exception;

public class FinancialAccountHasTransactionsException extends RuntimeException {

    public FinancialAccountHasTransactionsException() {
        super("Financial account has transactions.");
    }
}
