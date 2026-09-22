package com.rodrigs.finance_manager_api.shared.exception;

public class OnboardingVersionLaterException extends RuntimeException {
    public OnboardingVersionLaterException() {
        super("The onboarding version is later than the current one.");
    }
}
