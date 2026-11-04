package io.github.riseoftech.library.catalog.book;

import io.github.riseoftech.library.catalog.book.BookDtos.BookRequest;
import io.github.riseoftech.library.catalog.book.BookDtos.BookResponse;
import io.github.riseoftech.library.common.LikePatterns;
import io.github.riseoftech.library.common.PageResponse;
import io.github.riseoftech.library.common.UniqueConstraints;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BookService {

    static final String ISBN_CONSTRAINT = "uk_book_isbn";

    private static final Sort BY_TITLE = Sort.by("title", "id");

    private final BookRepository books;

    public BookService(BookRepository books) {
        this.books = books;
    }

    @Transactional
    public BookResponse create(BookRequest request) {
        String isbn = Isbn.normalize(request.isbn());
        if (books.existsByIsbn(isbn)) {
            throw new BookIsbnTakenException(isbn);
        }
        Book book = new Book(request.title(), request.authors(), isbn, request.publicationYear());
        return BookResponse.from(saveAndFlush(book, isbn));
    }

    @Transactional
    public BookResponse update(long id, BookRequest request) {
        Book book = load(id);
        String isbn = Isbn.normalize(request.isbn());
        if (books.existsByIsbnAndIdNot(isbn, id)) {
            throw new BookIsbnTakenException(isbn);
        }
        book.replace(request.title(), request.authors(), isbn, request.publicationYear());
        return BookResponse.from(saveAndFlush(book, isbn));
    }

    public BookResponse get(long id) {
        return BookResponse.from(load(id));
    }

    /**
     * A query that is a valid ISBN matches by ISBN only; anything else is a case-insensitive
     * substring of the title or an author name. A blank query returns all books.
     */
    public PageResponse<BookResponse> search(String query, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, BY_TITLE);
        Page<Book> result;
        if (query == null || query.isBlank()) {
            result = books.findAll(pageRequest);
        } else if (Isbn.isValid(query.strip())) {
            result = books.findByIsbn(Isbn.normalize(query.strip()), pageRequest);
        } else {
            result = books.searchByTitleOrAuthor(LikePatterns.contains(query.strip()), pageRequest);
        }
        return PageResponse.from(result, BookResponse::from);
    }

    private Book load(long id) {
        return books.findById(id).orElseThrow(() -> new BookNotFoundException(id));
    }

    private Book saveAndFlush(Book book, String isbn) {
        try {
            return books.saveAndFlush(book);
        } catch (DataIntegrityViolationException ex) {
            if (UniqueConstraints.isViolated(ex, ISBN_CONSTRAINT)) {
                throw new BookIsbnTakenException(isbn);
            }
            throw ex;
        }
    }
}
