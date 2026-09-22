package com.rodrigs.finance_manager_api.shared.exception;

public class OnboardingVersionPreviousException extends RuntimeException {
    public OnboardingVersionPreviousException() {
        super("The onboarding version is previous to the current one.");
    }
}
