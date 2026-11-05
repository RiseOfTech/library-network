package io.github.riseoftech.library.loans;

import io.github.riseoftech.library.catalog.copy.CopyStatus;
import io.github.riseoftech.library.common.ConflictException;

public class CopyNotAvailableException extends ConflictException {

    public static final String CODE = "COPY_NOT_AVAILABLE";

    public CopyNotAvailableException(long copyId, CopyStatus status) {
        super(CODE, "Copy " + copyId + " is " + status + " and cannot be checked out");
    }

    /** The copy already has an open loan (lost race on {@code uk_loan_open_copy}). */
    public CopyNotAvailableException(long copyId) {
        super(CODE, "Copy " + copyId + " is already on loan");
    }
}
