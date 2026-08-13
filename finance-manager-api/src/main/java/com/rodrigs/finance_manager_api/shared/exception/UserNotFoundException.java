package com.rodrigs.finance_manager_api.shared.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException() {
        super("Authenticated User not found.");
    }
}
