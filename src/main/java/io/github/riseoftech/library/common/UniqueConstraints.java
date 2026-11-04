package io.github.riseoftech.library.common;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Recognizes which named database constraint caused a {@link DataIntegrityViolationException}.
 * Used to map a lost uniqueness race to the same API error as the pre-check.
 */
public final class UniqueConstraints {

    private UniqueConstraints() {
    }

    public static boolean isViolated(DataIntegrityViolationException ex, String constraintName) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException cve && cve.getConstraintName() != null) {
                return cve.getConstraintName().equalsIgnoreCase(constraintName);
            }
        }
        return false;
    }
}
