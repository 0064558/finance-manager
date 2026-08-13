package com.rodrigs.finance_manager_api.shared.exception;

public class TransactionTypeMismatchException extends RuntimeException {
    public TransactionTypeMismatchException() {
        super("Transaction type must be the same type of category.");
    }
}
