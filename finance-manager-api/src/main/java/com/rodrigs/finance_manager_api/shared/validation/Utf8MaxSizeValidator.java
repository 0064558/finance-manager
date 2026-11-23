package com.rodrigs.finance_manager_api.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class Utf8MaxSizeValidator implements ConstraintValidator<Utf8MaxSize, String> {
    private int max;

    @Override
    public void initialize(Utf8MaxSize annotation) {
        max = annotation.value();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || (value.length() <= max && value.getBytes(StandardCharsets.UTF_8).length <= max);
    }
}
