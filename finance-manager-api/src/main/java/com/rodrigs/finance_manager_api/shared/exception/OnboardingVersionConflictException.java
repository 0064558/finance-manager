package com.rodrigs.finance_manager_api.shared.exception;

public class OnboardingVersionConflictException extends RuntimeException {
    public OnboardingVersionConflictException(String message) {
        super(message);
    }
}
