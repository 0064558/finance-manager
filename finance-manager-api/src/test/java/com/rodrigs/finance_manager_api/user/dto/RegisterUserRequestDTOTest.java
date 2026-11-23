package com.rodrigs.finance_manager_api.user.dto;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterUserRequestDTOTest {
    @Test
    void validatesPasswordLimitInUtf8Bytes() {
        String passwordAtLimit = "A1" + "é".repeat(33) + "😀";
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new RegisterUserRequestDTO("Rodrigo", "test@example.com", passwordAtLimit)))
                    .isEmpty();
            assertThat(validator.validate(new RegisterUserRequestDTO("Rodrigo", "test@example.com", passwordAtLimit + "a")))
                    .singleElement()
                    .satisfies(violation -> {
                        assertThat(violation.getPropertyPath().toString()).isEqualTo("password");
                        assertThat(violation.getMessage()).contains("72 bytes em UTF-8");
                    });
        }
    }
}
