package io.github.riseoftech.library.catalog.book;

import io.github.riseoftech.library.common.NotFoundException;

public class BookNotFoundException extends NotFoundException {

    public static final String CODE = "BOOK_NOT_FOUND";

    public BookNotFoundException(long id) {
        super(CODE, "Book " + id + " not found");
    }
}
