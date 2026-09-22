package com.rodrigs.finance_manager_api.user.entity;

import com.rodrigs.finance_manager_api.shared.exception.OnboardingVersionPreviousException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    @Test
    void shouldAdvanceOnboardingVersion() {
        User user = user();

        user.advanceOnboardingVersion(1);

        assertThat(user.getOnboardingVersion()).isEqualTo(1);
    }

    @Test
    void shouldTreatRepeatedOnboardingVersionAsIdempotent() {
        User user = user();
        user.advanceOnboardingVersion(1);

        user.advanceOnboardingVersion(1);

        assertThat(user.getOnboardingVersion()).isEqualTo(1);
    }

    @Test
    void shouldRejectOnboardingVersionRegression() {
        User user = user();
        user.advanceOnboardingVersion(1);

        assertThatThrownBy(() -> user.advanceOnboardingVersion(0))
                .isInstanceOf(OnboardingVersionPreviousException.class);
        assertThat(user.getOnboardingVersion()).isEqualTo(1);
    }

    private User user() {
        return new User("Rodrigo", "rodrigo@email.com", "hashed-password");
    }
}
