package io.github.riseoftech.library.catalog.copy;

import io.github.riseoftech.library.common.NotFoundException;

public class CopyNotFoundException extends NotFoundException {

    public static final String CODE = "COPY_NOT_FOUND";

    public CopyNotFoundException(long id) {
        super(CODE, "Copy " + id + " not found");
    }
}
