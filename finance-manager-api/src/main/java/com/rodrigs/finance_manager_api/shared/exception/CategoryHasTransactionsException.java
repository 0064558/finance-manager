package com.rodrigs.finance_manager_api.shared.exception;

public class CategoryHasTransactionsException extends RuntimeException {

    public CategoryHasTransactionsException() {
        super("Category already exists for this user and transaction type");
    }
}
