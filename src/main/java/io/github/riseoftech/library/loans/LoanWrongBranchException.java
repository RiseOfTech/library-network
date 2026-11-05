package io.github.riseoftech.library.loans;

import io.github.riseoftech.library.common.ConflictException;

public class LoanWrongBranchException extends ConflictException {

    public static final String CODE = "LOAN_WRONG_BRANCH";

    public LoanWrongBranchException(long copyId, long copyBranchId, long requestBranchId, String action) {
        super(CODE, "Copy " + copyId + " belongs to branch " + copyBranchId + " and cannot be " + action
                + " at branch " + requestBranchId);
    }
}
