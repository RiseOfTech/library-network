package io.github.riseoftech.library.catalog.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.List;

import io.github.riseoftech.library.IntegrationTest;
import io.github.riseoftech.library.common.ErrorCodes;
import io.github.riseoftech.library.common.UniqueConstraints;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;

class BookApiTest extends IntegrationTest {

    static final String DUNE_ISBN = "9780441172719";

    @Autowired
    BookRepository repository;

    @BeforeEach
    void cleanDatabase() {
        truncate("loan", "copy", "book_author", "book");
    }

    // --- create ---

    @Test
    void bookIsCreated() {
        var result = assertThat(post("""
                {"title": "Dune", "authors": ["Frank Herbert"], "isbn": "978-0-441-17271-9", "publicationYear": 1965}"""))
                .hasStatus(HttpStatus.CREATED);
        long id = repository.findAll().getFirst().getId();
        result.headers().hasValue("Location", "/api/books/" + id);
        var body = result.bodyJson();
        body.extractingPath("$.id").isEqualTo((int) id);
        body.extractingPath("$.title").isEqualTo("Dune");
        body.extractingPath("$.authors").asArray().containsExactly("Frank Herbert");
        body.extractingPath("$.isbn").isEqualTo(DUNE_ISBN);
        body.extractingPath("$.publicationYear").isEqualTo(1965);
    }

    @Test
    void publicationYearIsOptionalAndAuthorOrderIsKept() {
        var body = assertThat(post("""
                {"title": "Good Omens", "authors": ["Terry Pratchett", "Neil Gaiman"], "isbn": "%s"}"""
                .formatted(isbn(1))))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson();
        body.extractingPath("$.authors").asArray().containsExactly("Terry Pratchett", "Neil Gaiman");
        body.extractingPath("$.publicationYear").isNull();
    }

    @Test
    void blankTitleIsRejected() {
        assertValidationFailed(post("""
                {"title": " ", "authors": ["Frank Herbert"], "isbn": "%s"}""".formatted(DUNE_ISBN)), "title");
    }

    @Test
    void emptyAuthorsAreRejected() {
        assertValidationFailed(post("""
                {"title": "Dune", "authors": [], "isbn": "%s"}""".formatted(DUNE_ISBN)), "authors");
    }

    @Test
    void blankAuthorNameIsRejected() {
        assertValidationFailed(post("""
                {"title": "Dune", "authors": ["Frank Herbert", ""], "isbn": "%s"}""".formatted(DUNE_ISBN)),
                "authors[1]");
    }

    // --- ISBN rules ---

    @Test
    void isbnWithWrongCheckDigitIsRejected() {
        assertValidationFailed(post("""
                {"title": "Dune", "authors": ["Frank Herbert"], "isbn": "9780441172710"}"""), "isbn");
    }

    @Test
    void isbn10IsRejected() {
        assertValidationFailed(post("""
                {"title": "Dune", "authors": ["Frank Herbert"], "isbn": "0441172717"}"""), "isbn");
    }

    @Test
    void creatingBookWithExistingIsbn() {
        createBook("Dune", "Frank Herbert", DUNE_ISBN);

        assertThat(post("""
                {"title": "Dune (reprint)", "authors": ["Frank Herbert"], "isbn": "978-0441172719"}"""))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("BOOK_ISBN_TAKEN");
    }

    @Test
    void updatingBookToAnotherBooksIsbn() {
        long a = createBook("Solaris", "Stanislaw Lem", isbn(1));
        createBook("Dune", "Frank Herbert", DUNE_ISBN);

        assertThat(put(a, """
                {"title": "Solaris", "authors": ["Stanislaw Lem"], "isbn": "%s"}""".formatted(DUNE_ISBN)))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("BOOK_ISBN_TAKEN");

        assertThat(repository.findById(a)).get().extracting(Book::getIsbn).isEqualTo(isbn(1));
    }

    @Test
    void duplicateIsbnViolatesNamedConstraint() {
        repository.saveAndFlush(new Book("Dune", List.of("Frank Herbert"), DUNE_ISBN, null));

        var ex = catchThrowableOfType(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(new Book("Copy", List.of("Someone"), DUNE_ISBN, null)));

        assertThat(UniqueConstraints.isViolated(ex, BookService.ISBN_CONSTRAINT)).isTrue();
    }

    // --- update / get ---

    @Test
    void bookIsUpdated() {
        long id = createBook("Dune", "Frank Herbert", DUNE_ISBN);

        var body = assertThat(put(id, """
                {"title": "Dune (40th anniversary)", "authors": ["Frank Herbert", "Brian Herbert"],
                 "isbn": "%s", "publicationYear": 2005}""".formatted(DUNE_ISBN)))
                .hasStatus(HttpStatus.OK)
                .bodyJson();
        body.extractingPath("$.title").isEqualTo("Dune (40th anniversary)");
        body.extractingPath("$.authors").asArray().containsExactly("Frank Herbert", "Brian Herbert");
        body.extractingPath("$.publicationYear").isEqualTo(2005);
        body.extractingPath("$.isbn").isEqualTo(DUNE_ISBN);
    }

    @Test
    void updateIsValidatedLikeCreate() {
        long id = createBook("Dune", "Frank Herbert", DUNE_ISBN);

        assertValidationFailed(put(id, """
                {"title": "Dune", "authors": [], "isbn": "9780441172710"}"""), "authors", "isbn");
    }

    @Test
    void updatingUnknownBook() {
        assertThat(put(999, """
                {"title": "Dune", "authors": ["Frank Herbert"], "isbn": "%s"}""".formatted(DUNE_ISBN)))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("BOOK_NOT_FOUND");
    }

    @Test
    void getBookById() {
        long id = createBook("Dune", "Frank Herbert", DUNE_ISBN);

        var body = assertThat(mvc.get().uri("/api/books/{id}", id))
                .hasStatus(HttpStatus.OK)
                .bodyJson();
        body.extractingPath("$.id").isEqualTo((int) id);
        body.extractingPath("$.title").isEqualTo("Dune");
        body.extractingPath("$.authors").asArray().containsExactly("Frank Herbert");
    }

    @Test
    void getUnknownBook() {
        assertThat(mvc.get().uri("/api/books/999"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("BOOK_NOT_FOUND");
    }

    // --- search ---

    @Test
    void searchByTitleFragment() {
        createSearchFixture();

        assertThat(mvc.get().uri("/api/books?q=dune"))
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.items[*].title").asArray()
                .containsExactly("Dune", "Dune Messiah");
    }

    @Test
    void searchByAuthor() {
        createSearchFixture();

        var body = assertThat(mvc.get().uri("/api/books?q=herbert"))
                .hasStatus(HttpStatus.OK)
                .bodyJson();
        body.extractingPath("$.items[*].title").asArray().containsExactly("Dune", "Dune Messiah");
        body.extractingPath("$.totalElements").isEqualTo(2);
    }

    @Test
    void bookMatchingTitleAndAuthorIsReturnedOnce() {
        createBook("Herbert's Dune Notes", List.of("Frank Herbert", "Brian Herbert"), isbn(7));

        var body = assertThat(mvc.get().uri("/api/books?q=herbert"))
                .hasStatus(HttpStatus.OK)
                .bodyJson();
        body.extractingPath("$.items").asArray().hasSize(1);
        body.extractingPath("$.totalElements").isEqualTo(1);
    }

    @Test
    void searchByIsbn() {
        createSearchFixture();

        var body = assertThat(mvc.get().uri("/api/books?q=978-0-441-17271-9"))
                .hasStatus(HttpStatus.OK)
                .bodyJson();
        body.extractingPath("$.items[*].isbn").asArray().containsExactly(DUNE_ISBN);
        body.extractingPath("$.totalElements").isEqualTo(1);
    }

    @Test
    void wildcardCharactersAreTakenLiterally() {
        createSearchFixture();

        assertThat(mvc.get().uri("/api/books?q=%25"))
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.totalElements").isEqualTo(0);
    }

    @Test
    void nothingMatches() {
        createSearchFixture();

        var body = assertThat(mvc.get().uri("/api/books?q=nonexistent"))
                .hasStatus(HttpStatus.OK)
                .bodyJson();
        body.extractingPath("$.items").asArray().isEmpty();
        body.extractingPath("$.totalElements").isEqualTo(0);
    }

    @Test
    void withoutQueryAllBooksOrderedByTitle() {
        createSearchFixture();

        var body = assertThat(mvc.get().uri("/api/books"))
                .hasStatus(HttpStatus.OK)
                .bodyJson();
        body.extractingPath("$.items[*].title").asArray().containsExactly("Dune", "Dune Messiah", "Solaris");
        body.extractingPath("$.page").isEqualTo(0);
        body.extractingPath("$.size").isEqualTo(20);
    }

    // --- pagination ---

    @Test
    void secondPage() {
        for (int i = 1; i <= 25; i++) {
            createBook("Book %02d".formatted(i), "Author", isbn(100 + i));
        }

        var body = assertThat(mvc.get().uri("/api/books?page=1&size=10"))
                .hasStatus(HttpStatus.OK)
                .bodyJson();
        body.extractingPath("$.items").asArray().hasSize(10);
        body.extractingPath("$.items[0].title").isEqualTo("Book 11");
        body.extractingPath("$.page").isEqualTo(1);
        body.extractingPath("$.size").isEqualTo(10);
        body.extractingPath("$.totalElements").isEqualTo(25);
        body.extractingPath("$.totalPages").isEqualTo(3);
    }

    @Test
    void pageSizeOutOfRange() {
        var body = assertThat(mvc.get().uri("/api/books?size=500"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson();
        body.extractingPath("$.code").isEqualTo(ErrorCodes.VALIDATION_FAILED);
        body.extractingPath("$.errors[*].field").asArray().containsExactly("size");
    }

    @Test
    void negativePageIsRejected() {
        assertThat(mvc.get().uri("/api/books?page=-1"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo(ErrorCodes.VALIDATION_FAILED);
    }

    // --- helpers ---

    private void createSearchFixture() {
        createBook("Solaris", "Stanislaw Lem", isbn(1));
        createBook("Dune Messiah", "Frank Herbert", isbn(2));
        createBook("Dune", "Frank Herbert", DUNE_ISBN);
    }

    private long createBook(String title, String author, String isbn) {
        return createBook(title, List.of(author), isbn);
    }

    private long createBook(String title, List<String> authors, String isbn) {
        return repository.save(new Book(title, authors, isbn, null)).getId();
    }

    /** A valid ISBN-13 derived from {@code n}. */
    static String isbn(int n) {
        String first12 = "979%09d".formatted(n);
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            sum += (first12.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        }
        return first12 + (10 - sum % 10) % 10;
    }

    private MockMvcRequestBuilder post(String json) {
        return mvc.post().uri("/api/books").contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private MockMvcRequestBuilder put(long id, String json) {
        return mvc.put().uri("/api/books/{id}", id).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private void assertValidationFailed(MockMvcRequestBuilder request, String... fields) {
        var body = assertThat(request)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson();
        body.extractingPath("$.code").isEqualTo(ErrorCodes.VALIDATION_FAILED);
        body.extractingPath("$.errors[*].field").asArray().containsExactlyInAnyOrder((Object[]) fields);
    }
}
