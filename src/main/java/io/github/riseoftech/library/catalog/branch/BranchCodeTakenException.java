package io.github.riseoftech.library.catalog.branch;

import io.github.riseoftech.library.common.ConflictException;

public class BranchCodeTakenException extends ConflictException {

    public static final String CODE = "BRANCH_CODE_TAKEN";

    public BranchCodeTakenException(String code) {
        super(CODE, "Branch code " + code + " is already taken");
    }
}
