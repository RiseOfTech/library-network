package io.github.riseoftech.library.catalog.branch;

import io.github.riseoftech.library.common.NotFoundException;

public class BranchNotFoundException extends NotFoundException {

    public static final String CODE = "BRANCH_NOT_FOUND";

    public BranchNotFoundException(long id) {
        super(CODE, "Branch " + id + " not found");
    }
}
