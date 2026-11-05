package io.github.riseoftech.library.loans;

import io.github.riseoftech.library.common.ConflictException;

public class LoanAlreadyReturnedException extends ConflictException {

    public static final String CODE = "LOAN_ALREADY_RETURNED";

    public LoanAlreadyReturnedException(Long id) {
        super(CODE, "Loan " + id + " is already returned");
    }
}
