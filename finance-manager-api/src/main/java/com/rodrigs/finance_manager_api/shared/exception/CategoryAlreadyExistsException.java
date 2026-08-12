package com.rodrigs.finance_manager_api.shared.exception;

public class CategoryAlreadyExistsException extends RuntimeException {

    public CategoryAlreadyExistsException() {
        super("Category already exists for this user and transaction type");
    }
}
