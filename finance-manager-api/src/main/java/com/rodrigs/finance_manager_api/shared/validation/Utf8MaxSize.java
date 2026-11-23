package com.rodrigs.finance_manager_api.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Constraint(validatedBy = Utf8MaxSizeValidator.class)
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
public @interface Utf8MaxSize {
    int value();
    String message() default "A senha deve ter no máximo {value} bytes em UTF-8.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
