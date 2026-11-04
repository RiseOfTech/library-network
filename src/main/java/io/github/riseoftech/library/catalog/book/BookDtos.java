package io.github.riseoftech.library.catalog.book;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class BookDtos {

    private BookDtos() {
    }

    /** Used for both create and full update. */
    public record BookRequest(
            @NotBlank @Size(max = 500) String title,
            @NotEmpty List<@NotBlank @Size(max = 200) String> authors,
            @NotNull @ValidIsbn String isbn,
            Integer publicationYear) {
    }

    public record BookResponse(long id, String title, List<String> authors, String isbn, Integer publicationYear) {

        static BookResponse from(Book book) {
            return new BookResponse(book.getId(), book.getTitle(), book.getAuthors(), book.getIsbn(),
                    book.getPublicationYear());
        }
    }
}
