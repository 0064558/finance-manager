package com.rodrigs.finance_manager_api.shared.exception;

public class TransactionNotFoundException extends RuntimeException {
    public TransactionNotFoundException() {
        super("Transaction not found for the authenticated user.");
    }
}
