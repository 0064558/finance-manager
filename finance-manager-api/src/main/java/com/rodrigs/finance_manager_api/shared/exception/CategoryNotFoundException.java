package com.rodrigs.finance_manager_api.shared.exception;

public class CategoryNotFoundException extends RuntimeException {
    public CategoryNotFoundException() {
        super("Category not found for the authenticated user.");
    }
}
