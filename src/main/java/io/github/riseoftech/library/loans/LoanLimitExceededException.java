package io.github.riseoftech.library.loans;

import io.github.riseoftech.library.common.ConflictException;

public class LoanLimitExceededException extends ConflictException {

    public static final String CODE = "LOAN_LIMIT_EXCEEDED";

    public LoanLimitExceededException(long readerId, String category, int loanLimit) {
        super(CODE, "Reader " + readerId + " already has " + loanLimit
                + " open loans, the limit of category " + category);
    }
}
