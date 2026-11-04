package io.github.riseoftech.library.loans;

import io.github.riseoftech.library.common.NotFoundException;

public class LoanNotFoundException extends NotFoundException {

    public static final String CODE = "LOAN_NOT_FOUND";

    public LoanNotFoundException(long id) {
        super(CODE, "Loan " + id + " not found");
    }
}
