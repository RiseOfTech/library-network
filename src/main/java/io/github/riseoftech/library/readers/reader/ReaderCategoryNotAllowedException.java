package io.github.riseoftech.library.readers.reader;

import java.time.LocalDate;

import io.github.riseoftech.library.common.ConflictException;

public class ReaderCategoryNotAllowedException extends ConflictException {

    public static final String CODE = "READER_CATEGORY_NOT_ALLOWED";

    public ReaderCategoryNotAllowedException(String category, LocalDate dateOfBirth) {
        super(CODE, "Category " + category + " is not allowed for a reader born on " + dateOfBirth);
    }

    /** The reader's stored category is no longer among the configured categories. */
    public ReaderCategoryNotAllowedException(long readerId, String category) {
        super(CODE, "Reader " + readerId + " has category " + category + ", which is not configured");
    }
}
