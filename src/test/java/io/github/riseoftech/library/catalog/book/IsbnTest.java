package io.github.riseoftech.library.catalog.book;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IsbnTest {

    @ParameterizedTest
    @ValueSource(strings = {"9780441172719", "9780306406157", "9781861972712", "9790000000001"})
    void validIsbn13(String isbn) {
        assertThat(Isbn.isValid(isbn)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"9780441172710", "9780441172718", "9780306406158"})
    void wrongCheckDigit(String isbn) {
        assertThat(Isbn.isValid(isbn)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0441172717", "978044117271", "97804411727190", ""})
    void wrongLength(String isbn) {
        assertThat(Isbn.isValid(isbn)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"978044117271X", "978-0-441-ABCDE-9"})
    void nonDigits(String isbn) {
        assertThat(Isbn.isValid(isbn)).isFalse();
    }

    @Test
    void nullIsInvalid() {
        assertThat(Isbn.isValid(null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"978-0-441-17271-9", "978 0 441 17271 9", "978-0441172719", " 978-0-441 17271-9 "})
    void hyphensAndSpacesAreIgnored(String isbn) {
        assertThat(Isbn.isValid(isbn)).isTrue();
        assertThat(Isbn.normalize(isbn)).isEqualTo("9780441172719");
    }
}
