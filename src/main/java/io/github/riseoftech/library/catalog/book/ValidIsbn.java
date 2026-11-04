package io.github.riseoftech.library.catalog.book;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * The annotated string must be a valid ISBN-13; hyphens and spaces are allowed.
 * {@code null} is considered valid, combine with {@code @NotNull} when required.
 */
@Documented
@Constraint(validatedBy = ValidIsbn.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidIsbn {

    String message() default "must be a valid ISBN-13";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidIsbn, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || Isbn.isValid(value);
        }
    }
}
