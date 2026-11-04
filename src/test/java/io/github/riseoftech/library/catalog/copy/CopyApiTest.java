package io.github.riseoftech.library.catalog.copy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.LocalDate;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import io.github.riseoftech.library.IntegrationTest;
import io.github.riseoftech.library.catalog.book.Book;
import io.github.riseoftech.library.catalog.book.BookRepository;
import io.github.riseoftech.library.catalog.branch.Branch;
import io.github.riseoftech.library.catalog.branch.BranchRepository;
import io.github.riseoftech.library.common.ErrorCodes;
import io.github.riseoftech.library.common.UniqueConstraints;
import io.github.riseoftech.library.readers.reader.Reader;
import io.github.riseoftech.library.readers.reader.ReaderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;

class CopyApiTest extends IntegrationTest {

    @Autowired
    CopyRepository copies;

    @Autowired
    BookRepository books;

    @Autowired
    BranchRepository branches;

    @Autowired
    ReaderRepository readers;

    Book book;
    Branch central;
    Branch north;

    @BeforeEach
    void setUp() {
        truncate("loan", "copy", "book_author", "book", "branch", "reader");
        book = books.save(new Book("Dune", List.of("Frank Herbert"), "9780441172719", 1965));
        central = branches.save(new Branch("CENTRAL", "Central Library", "1 Main St"));
        north = branches.save(new Branch("NORTH", "North Library", "9 North Rd"));
    }

    // --- register ---

    @Test
    void copyIsRegistered() {
        var result = assertThat(register(book.getId(), central.getId(), "CEN-000123"))
                .hasStatus(HttpStatus.CREATED);
        long id = copies.findAll().getFirst().getId();
        result.headers().hasValue("Location", "/api/copies/" + id);
        var body = result.bodyJson();
        body.extractingPath("$.id").isEqualTo((int) id);
        body.extractingPath("$.bookId").isEqualTo(book.getId().intValue());
        body.extractingPath("$.branchId").isEqualTo(central.getId().intValue());
        body.extractingPath("$.inventoryNumber").isEqualTo("CEN-000123");
        body.extractingPath("$.status").isEqualTo("ON_SHELF");
    }

    @Test
    void inventoryNumberIsAlreadyTaken() {
        copy(central, "CEN-000123");

        assertThat(register(book.getId(), north.getId(), "CEN-000123"))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("COPY_INVENTORY_NUMBER_TAKEN");
    }

    @Test
    void duplicateInventoryNumberViolatesNamedConstraint() {
        copy(central, "CEN-000123");

        var ex = catchThrowableOfType(DataIntegrityViolationException.class,
                () -> copies.saveAndFlush(new Copy(book, north, "CEN-000123")));

        assertThat(UniqueConstraints.isViolated(ex, CopyService.INVENTORY_NUMBER_CONSTRAINT)).isTrue();
    }

    @Test
    void unknownBook() {
        assertThat(register(999L, central.getId(), "CEN-000123"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("BOOK_NOT_FOUND");
    }

    @Test
    void unknownBranch() {
        assertThat(register(book.getId(), 999L, "CEN-000123"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("BRANCH_NOT_FOUND");
    }

    @Test
    void missingBookIdIsRejected() {
        var body = assertThat(mvc.post().uri("/api/copies").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"branchId": %d, "inventoryNumber": "CEN-000123"}""".formatted(central.getId())))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson();
        body.extractingPath("$.code").isEqualTo(ErrorCodes.VALIDATION_FAILED);
        body.extractingPath("$.errors[*].field").asArray().containsExactly("bookId");
    }

    @Test
    void malformedInventoryNumberIsRejected() {
        var body = assertThat(register(book.getId(), central.getId(), "x"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson();
        body.extractingPath("$.code").isEqualTo(ErrorCodes.VALIDATION_FAILED);
        body.extractingPath("$.errors[*].field").asArray().containsExactly("inventoryNumber");
    }

    @Test
    void statusCannotBeSetByClient() {
        assertThat(mvc.post().uri("/api/copies").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bookId": %d, "branchId": %d, "inventoryNumber": "CEN-000123", "status": "LOST"}"""
                                .formatted(book.getId(), central.getId())))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.status").isEqualTo("ON_SHELF");
    }

    // --- get ---

    @Test
    void statusIsPartOfTheCopy() {
        Copy copy = copy(central, "CEN-1", CopyStatus.ON_LOAN);

        var body = assertThat(mvc.get().uri("/api/copies/{id}", copy.getId()))
                .hasStatus(HttpStatus.OK)
                .bodyJson();
        body.extractingPath("$.inventoryNumber").isEqualTo("CEN-1");
        body.extractingPath("$.status").isEqualTo("ON_LOAN");
    }

    @Test
    void unknownCopy() {
        assertThat(mvc.get().uri("/api/copies/999"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("COPY_NOT_FOUND");
    }

    // --- lost / found ---

    @Test
    void copyOnShelfIsMarkedLost() {
        Copy copy = copy(central, "CEN-1");

        assertThat(mvc.post().uri("/api/copies/{id}/lost", copy.getId()))
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.status").isEqualTo("LOST");
        assertThat(statusOf(copy)).isEqualTo(CopyStatus.LOST);
    }

    @Test
    void copyNotOnShelfCannotBeMarkedLost() {
        Copy copy = copy(central, "CEN-1", CopyStatus.LOST);

        assertThat(mvc.post().uri("/api/copies/{id}/lost", copy.getId()))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("COPY_STATUS_CONFLICT");
        assertThat(statusOf(copy)).isEqualTo(CopyStatus.LOST);
    }

    @Test
    void copyOnLoanCannotBeMarkedLost() throws Exception {
        Copy copy = copy(central, "CEN-1");
        long loanId = checkOut(copy);

        assertThat(mvc.post().uri("/api/copies/{id}/lost", copy.getId()))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("COPY_STATUS_CONFLICT");
        assertThat(statusOf(copy)).isEqualTo(CopyStatus.ON_LOAN);
        assertThat(mvc.get().uri("/api/loans/{id}", loanId))
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.status").isEqualTo("OPEN");
    }

    @Test
    void lostCopyIsFound() {
        Copy copy = copy(central, "CEN-1", CopyStatus.LOST);

        var body = assertThat(mvc.post().uri("/api/copies/{id}/found", copy.getId()))
                .hasStatus(HttpStatus.OK)
                .bodyJson();
        body.extractingPath("$.status").isEqualTo("ON_SHELF");
        body.extractingPath("$.branchId").isEqualTo(central.getId().intValue());
        assertThat(statusOf(copy)).isEqualTo(CopyStatus.ON_SHELF);
    }

    @Test
    void copyNotLostCannotBeFound() {
        Copy copy = copy(central, "CEN-1");

        assertThat(mvc.post().uri("/api/copies/{id}/found", copy.getId()))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("COPY_STATUS_CONFLICT");
        assertThat(statusOf(copy)).isEqualTo(CopyStatus.ON_SHELF);
    }

    @Test
    void markingUnknownCopyLost() {
        assertThat(mvc.post().uri("/api/copies/999/lost"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("COPY_NOT_FOUND");
    }

    @Test
    void staleCopyVersionIsRejected() {
        Copy stale = copy(central, "CEN-1");
        assertThat(mvc.post().uri("/api/copies/{id}/lost", stale.getId())).hasStatus(HttpStatus.OK);

        ReflectionTestUtils.setField(stale, "status", CopyStatus.ON_LOAN);

        assertThatThrownBy(() -> copies.saveAndFlush(stale))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    // --- list ---

    @Test
    void copiesOfBookInBranch() {
        Book other = books.save(new Book("Solaris", List.of("Stanislaw Lem"), "9780156027601", 1961));
        copy(central, "CEN-2");
        copy(central, "CEN-1");
        copy(north, "NOR-1");
        copies.save(new Copy(other, central, "CEN-3"));

        var body = assertThat(mvc.get().uri("/api/copies?bookId={book}&branchId={branch}",
                        book.getId(), central.getId()))
                .hasStatus(HttpStatus.OK)
                .bodyJson();
        body.extractingPath("$.items[*].inventoryNumber").asArray().containsExactly("CEN-1", "CEN-2");
        body.extractingPath("$.totalElements").isEqualTo(2);
    }

    @Test
    void filterByStatus() {
        copy(central, "CEN-1", CopyStatus.LOST);
        copy(central, "CEN-2");
        copy(north, "NOR-1", CopyStatus.LOST);

        var body = assertThat(mvc.get().uri("/api/copies?status=LOST"))
                .hasStatus(HttpStatus.OK)
                .bodyJson();
        body.extractingPath("$.items[*].inventoryNumber").asArray().containsExactly("CEN-1", "NOR-1");
        body.extractingPath("$.items[*].status").asArray().containsOnly("LOST");
    }

    @Test
    void listWithoutFiltersIsPaginated() {
        for (int i = 1; i <= 5; i++) {
            copy(central, "CEN-" + i);
        }

        var body = assertThat(mvc.get().uri("/api/copies?page=1&size=2"))
                .hasStatus(HttpStatus.OK)
                .bodyJson();
        body.extractingPath("$.items[*].inventoryNumber").asArray().containsExactly("CEN-3", "CEN-4");
        body.extractingPath("$.page").isEqualTo(1);
        body.extractingPath("$.size").isEqualTo(2);
        body.extractingPath("$.totalElements").isEqualTo(5);
        body.extractingPath("$.totalPages").isEqualTo(3);
    }

    @Test
    void unknownStatusValue() {
        var body = assertThat(mvc.get().uri("/api/copies?status=BROKEN"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson();
        body.extractingPath("$.code").isEqualTo(ErrorCodes.VALIDATION_FAILED);
        body.extractingPath("$.errors[*].field").asArray().containsExactly("status");
    }

    @Test
    void listPageSizeOutOfRange() {
        assertThat(mvc.get().uri("/api/copies?size=101"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo(ErrorCodes.VALIDATION_FAILED);
    }

    // --- availability ---

    @Test
    void availabilityAcrossBranches() {
        copy(north, "NOR-1");
        copy(central, "CEN-1");
        copy(central, "CEN-2");
        copy(central, "CEN-3", CopyStatus.LOST);

        var body = assertThat(mvc.get().uri("/api/books/{id}/availability", book.getId()))
                .hasStatus(HttpStatus.OK)
                .bodyJson();
        body.extractingPath("$[*].branchCode").asArray().containsExactly("CENTRAL", "NORTH");
        body.extractingPath("$[0].branchId").isEqualTo(central.getId().intValue());
        body.extractingPath("$[0].total").isEqualTo(2);
        body.extractingPath("$[0].onShelf").isEqualTo(2);
        body.extractingPath("$[1].branchId").isEqualTo(north.getId().intValue());
        body.extractingPath("$[1].total").isEqualTo(1);
        body.extractingPath("$[1].onShelf").isEqualTo(1);
    }

    @Test
    void copiesNotOnShelfCountTowardsTotalOnly() {
        copy(central, "CEN-1");
        copy(central, "CEN-2", CopyStatus.ON_LOAN);
        copy(central, "CEN-3", CopyStatus.IN_TRANSIT);

        var body = assertThat(mvc.get().uri("/api/books/{id}/availability", book.getId()))
                .hasStatus(HttpStatus.OK)
                .bodyJson();
        body.extractingPath("$[0].total").isEqualTo(3);
        body.extractingPath("$[0].onShelf").isEqualTo(1);
    }

    @Test
    void copiesOnLoanAreCountedButNotOnShelf() throws Exception {
        Copy copy = copy(central, "CEN-1");
        copy(central, "CEN-2");
        assertAvailability(2, 2);

        long loanId = checkOut(copy);
        assertAvailability(2, 1);

        assertThat(mvc.post().uri("/api/loans/{id}/return", loanId).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"branchId": %d}""".formatted(central.getId())))
                .hasStatus(HttpStatus.OK);
        assertAvailability(2, 2);
    }

    @Test
    void bookWithoutCopies() {
        assertThat(mvc.get().uri("/api/books/{id}/availability", book.getId()))
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$").asArray().isEmpty();
    }

    @Test
    void availabilityOfUnknownBook() {
        assertThat(mvc.get().uri("/api/books/999/availability"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("BOOK_NOT_FOUND");
    }

    // --- helpers ---

    private MockMvcRequestBuilder register(Long bookId, Long branchId, String inventoryNumber) {
        return mvc.post().uri("/api/copies").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"bookId": %d, "branchId": %d, "inventoryNumber": "%s"}"""
                        .formatted(bookId, branchId, inventoryNumber));
    }

    private Copy copy(Branch branch, String inventoryNumber) {
        return copies.save(new Copy(book, branch, inventoryNumber));
    }

    /** Statuses other than ON_SHELF/LOST are reached through later changes, so set them directly. */
    private Copy copy(Branch branch, String inventoryNumber, CopyStatus status) {
        Copy copy = new Copy(book, branch, inventoryNumber);
        ReflectionTestUtils.setField(copy, "status", status);
        return copies.save(copy);
    }

    /** Checks the copy out through the loans API to a new adult reader and returns the loan id. */
    private long checkOut(Copy copy) throws Exception {
        Reader reader = readers.save(new Reader("LN-000001", "Anna", "Petrova", LocalDate.of(1990, 5, 17),
                "ADULT", "anna@example.org", null));
        var result = mvc.post().uri("/api/loans").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"readerId": %d, "copyId": %d, "branchId": %d}"""
                        .formatted(reader.getId(), copy.getId(), copy.getBranch().getId()))
                .exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private void assertAvailability(int total, int onShelf) {
        var body = assertThat(mvc.get().uri("/api/books/{id}/availability", book.getId()))
                .hasStatus(HttpStatus.OK)
                .bodyJson();
        body.extractingPath("$[0].branchCode").isEqualTo("CENTRAL");
        body.extractingPath("$[0].total").isEqualTo(total);
        body.extractingPath("$[0].onShelf").isEqualTo(onShelf);
    }

    private CopyStatus statusOf(Copy copy) {
        return copies.findById(copy.getId()).orElseThrow().getStatus();
    }
}
