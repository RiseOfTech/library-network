package io.github.riseoftech.library.catalog.copy;

import io.github.riseoftech.library.common.ConflictException;

public class CopyStatusConflictException extends ConflictException {

    public static final String CODE = "COPY_STATUS_CONFLICT";

    public CopyStatusConflictException(Long id, CopyStatus current, String action) {
        super(CODE, "Copy " + id + " is " + current + " and cannot be " + action);
    }
}
