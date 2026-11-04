package io.github.riseoftech.library.catalog.book;

import io.github.riseoftech.library.common.ConflictException;

public class BookIsbnTakenException extends ConflictException {

    public static final String CODE = "BOOK_ISBN_TAKEN";

    public BookIsbnTakenException(String isbn) {
        super(CODE, "A book with ISBN " + isbn + " already exists");
    }
}
