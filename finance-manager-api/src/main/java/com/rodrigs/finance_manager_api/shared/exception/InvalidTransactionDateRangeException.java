package com.rodrigs.finance_manager_api.shared.exception;

public class InvalidTransactionDateRangeException extends RuntimeException {

    public InvalidTransactionDateRangeException() {
        super("The transaction start date must be before or equal to the end date.");
    }
}
