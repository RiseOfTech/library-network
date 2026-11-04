package io.github.riseoftech.library.readers.reader;

import io.github.riseoftech.library.common.NotFoundException;

public class ReaderNotFoundException extends NotFoundException {

    public static final String CODE = "READER_NOT_FOUND";

    public ReaderNotFoundException(long id) {
        super(CODE, "Reader " + id + " not found");
    }
}
