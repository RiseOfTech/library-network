package io.github.riseoftech.library.readers.reader;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.github.riseoftech.library.readers.reader.ReaderDtos.ReaderRequest;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * A reader request must have a non-blank {@code email} or {@code phone}.
 * The violation is reported on {@code email}, so it appears as a field error.
 */
@Documented
@Constraint(validatedBy = ContactRequired.Validator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ContactRequired {

    String message() default "email or phone is required";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ContactRequired, ReaderRequest> {

        @Override
        public boolean isValid(ReaderRequest request, ConstraintValidatorContext context) {
            if (request == null || hasText(request.email()) || hasText(request.phone())) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("email")
                    .addConstraintViolation();
            return false;
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }
}
