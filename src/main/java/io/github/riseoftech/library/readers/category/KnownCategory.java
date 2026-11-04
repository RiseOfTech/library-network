package io.github.riseoftech.library.readers.category;

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
 * The annotated string must be the code of a configured reader category, matched exactly.
 * {@code null} is considered valid, combine with {@code @NotNull} when required.
 * The validator is created by Spring, so it is available only with the Spring-managed validator.
 */
@Documented
@Constraint(validatedBy = KnownCategory.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface KnownCategory {

    String message() default "must be a known reader category";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<KnownCategory, String> {

        private final ReaderCategories categories;

        public Validator(ReaderCategories categories) {
            this.categories = categories;
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || categories.find(value).isPresent();
        }
    }
}
