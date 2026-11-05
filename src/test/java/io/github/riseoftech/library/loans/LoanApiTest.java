package io.github.riseoftech.library.loans;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.jayway.jsonpath.JsonPath;
import io.github.riseoftech.library.FixedClockConfiguration;
import io.github.riseoftech.library.IntegrationTest;
import io.github.riseoftech.library.catalog.book.Book;
import io.github.riseoftech.library.catalog.book.BookRepository;
import io.github.riseoftech.library.catalog.branch.Branch;
import io.github.riseoftech.library.catalog.branch.BranchRepository;
import io.github.riseoftech.library.catalog.copy.Copy;
import io.github.riseoftech.library.catalog.copy.CopyRepository;
import io.github.riseoftech.library.catalog.copy.CopyStatus;
import io.github.riseoftech.library.common.ErrorCodes;
import io.github.riseoftech.library.readers.reader.Reader;
import io.github.riseoftech.library.readers.reader.ReaderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;

/**
 * Loan API scenarios; today is fixed at 2026-11-04 (12:00 UTC).
 */
class LoanApiTest extends IntegrationTest {

    private static final String NOW = "2026-11-04T12:00:00Z";
    private static final LocalDate ADULT_BIRTH = LocalDate.of(1990, 5, 17);
    private static final LocalDate CHILD_BIRTH = LocalDate.of(2016, 3, 1);

    @Autowired
    LoanRepository loans;

    @Autowired
    ReaderRepository readers;

    @Autowired
    CopyRepository copies;

    @Autowired
    BookRepository books;

    @Autowired
    BranchRepository branches;

    Book book;
    Branch central;
    Branch north;
    int cardSeq;
    int inventorySeq;

    @BeforeEach
    void setUp() {
        truncate("loan", "copy", "book_author", "book", "branch", "reader");
        book = books.save(new Book("Dune", List.of("Frank Herbert"), "9780441172719", 1965));
        central = branches.save(new Branch("CENTRAL", "Central Library", "1 Main St"));
        north = branches.save(new Branch("NORTH", "North Library", "9 North Rd"));
    }

    // --- check out a copy ---

    @Test
    void copyIsCheckedOut() {
        Reader reader = reader("ADULT", ADULT_BIRTH);
        Copy copy = copy(central);

        var result = assertThat(checkout(reader, copy, central)).hasStatus(HttpStatus.CREATED);
        long id = loans.findAll().getFirst().getId();
        result.headers().hasValue("Location", "/api/loans/" + id);
        var body = result.bodyJson();
        body.extractingPath("$.id").isEqualTo((int) id);
        body.extractingPath("$.reader.id").isEqualTo(reader.getId().intValue());
        body.extractingPath("$.reader.cardNumber").isEqualTo(reader.getCardNumber());
        body.extractingPath("$.copy.id").isEqualTo(copy.getId().intValue());
        body.extractingPath("$.copy.inventoryNumber").isEqualTo(copy.getInventoryNumber());
        body.extractingPath("$.copy.bookId").isEqualTo(book.getId().intValue());
        body.extractingPath("$.branch.id").isEqualTo(central.getId().intValue());
        body.extractingPath("$.branch.code").isEqualTo("CENTRAL");
        body.extractingPath("$.status").isEqualTo("OPEN");
        body.extractingPath("$.checkedOutAt").isEqualTo(NOW);
        body.extractingPath("$.dueDate").isEqualTo("2026-11-25");
        body.extractingPath("$.loanPeriodDays").isEqualTo(21);
        body.extractingPath("$.returnedAt").isNull();
        body.extractingPath("$.overdue").isEqualTo(false);

        assertThat(mvc.get().uri("/api/copies/{id}", copy.getId()))
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.status").isEqualTo("ON_LOAN");
    }

    @Test
    void unknownReader() {
        Copy copy = copy(central);

        assertThat(checkout(999L, copy.getId(), central.getId()))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("READER_NOT_FOUND");
        assertThat(loanCount()).isZero();
        assertThat(statusOf(copy)).isEqualTo(CopyStatus.ON_SHELF);
    }

    @Test
    void unknownCopy() {
        Reader reader = reader("ADULT", ADULT_BIRTH);

        assertThat(checkout(reader.getId(), 999L, central.getId()))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("COPY_NOT_FOUND");
        assertThat(loanCount()).isZero();
    }

    @Test
    void unknownBranch() {
        Reader reader = reader("ADULT", ADULT_BIRTH);
        Copy copy = copy(central);

        assertThat(checkout(reader.getId(), copy.getId(), 999L))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("BRANCH_NOT_FOUND");
        assertThat(loanCount()).isZero();
    }

    @Test
    void missingFields() {
        var body = assertThat(mvc.post().uri("/api/loans").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson();
        body.extractingPath("$.code").isEqualTo(ErrorCodes.VALIDATION_FAILED);
        body.extractingPath("$.errors[*].field").asArray()
                .containsExactlyInAnyOrder("readerId", "copyId", "branchId");
    }

    // --- only a copy on the shelf ---

    @Test
    void copyAlreadyOnLoan() throws Exception {
        Copy copy = copy(central);
        long loanId = checkedOut(reader("ADULT", ADULT_BIRTH), copy, central);
        Reader other = reader("ADULT", ADULT_BIRTH);

        assertThat(checkout(other, copy, central))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("COPY_NOT_AVAILABLE");
        assertThat(loanCount()).isEqualTo(1);
        var existing = assertThat(getLoan(loanId)).hasStatus(HttpStatus.OK).bodyJson();
        existing.extractingPath("$.status").isEqualTo("OPEN");
        existing.extractingPath("$.returnedAt").isNull();
        assertThat(statusOf(copy)).isEqualTo(CopyStatus.ON_LOAN);
    }

    @Test
    void lostCopy() {
        Copy lost = new Copy(book, central, nextInventoryNumber());
        lost.markLost();
        Copy copy = copies.save(lost);

        assertThat(checkout(reader("ADULT", ADULT_BIRTH), copy, central))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("COPY_NOT_AVAILABLE");
        assertThat(statusOf(copy)).isEqualTo(CopyStatus.LOST);
        assertThat(loanCount()).isZero();
    }

    // --- checkout only in the copy's branch ---

    @Test
    void checkoutInCopysBranch() {
        Copy copy = copy(north);

        var body = assertThat(checkout(reader("ADULT", ADULT_BIRTH), copy, north))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson();
        body.extractingPath("$.branch.id").isEqualTo(north.getId().intValue());
        body.extractingPath("$.branch.code").isEqualTo("NORTH");
    }

    @Test
    void checkoutInAnotherBranch() {
        Copy copy = copy(north);

        assertThat(checkout(reader("ADULT", ADULT_BIRTH), copy, central))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("LOAN_WRONG_BRANCH");
        assertThat(statusOf(copy)).isEqualTo(CopyStatus.ON_SHELF);
        assertThat(loanCount()).isZero();
    }

    // --- due date from the reader category ---

    @Test
    void dueDateForAdult() {
        var body = assertThat(checkout(reader("ADULT", ADULT_BIRTH), copy(central), central))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson();
        body.extractingPath("$.dueDate").isEqualTo("2026-11-25");
        body.extractingPath("$.loanPeriodDays").isEqualTo(21);
    }

    @Test
    void dueDateForChild() {
        var body = assertThat(checkout(reader("CHILD", CHILD_BIRTH), copy(central), central))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson();
        body.extractingPath("$.dueDate").isEqualTo("2026-11-18");
        body.extractingPath("$.loanPeriodDays").isEqualTo(14);
    }

    @Test
    void categoryChangeKeepsDueDate() throws Exception {
        Reader reader = reader("ADULT", ADULT_BIRTH);
        long loanId = checkedOut(reader, copy(central), central);

        assertThat(updateCategory(reader, "STUDENT")).hasStatus(HttpStatus.OK);

        var body = assertThat(getLoan(loanId)).hasStatus(HttpStatus.OK).bodyJson();
        body.extractingPath("$.dueDate").isEqualTo("2026-11-25");
        body.extractingPath("$.loanPeriodDays").isEqualTo(21);
    }

    @Test
    void readerCategoryNoLongerConfigured() {
        Reader reader = reader("ADULT", ADULT_BIRTH);
        jdbc.update("update reader set category = 'SENIOR' where id = ?", reader.getId());
        Copy copy = copy(central);

        assertThat(checkout(reader, copy, central))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("READER_CATEGORY_NOT_ALLOWED");
        assertThat(loanCount()).isZero();
        assertThat(statusOf(copy)).isEqualTo(CopyStatus.ON_SHELF);
    }

    // --- loan limit ---

    @Test
    void lastLoanWithinLimit() throws Exception {
        Reader child = reader("CHILD", CHILD_BIRTH);
        checkedOut(child, copy(central), central);
        checkedOut(child, copy(central), central);

        assertThat(checkout(child, copy(central), central)).hasStatus(HttpStatus.CREATED);
        assertThat(loans.countOpenByReader(child.getId())).isEqualTo(3);
    }

    @Test
    void limitReached() throws Exception {
        Reader child = reader("CHILD", CHILD_BIRTH);
        for (int i = 0; i < 3; i++) {
            checkedOut(child, copy(central), central);
        }
        Copy copy = copy(central);

        assertThat(checkout(child, copy, central))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("LOAN_LIMIT_EXCEEDED");
        assertThat(statusOf(copy)).isEqualTo(CopyStatus.ON_SHELF);
        assertThat(loans.countOpenByReader(child.getId())).isEqualTo(3);
    }

    @Test
    void returnedLoansFreeTheLimit() throws Exception {
        Reader child = reader("CHILD", CHILD_BIRTH);
        long first = checkedOut(child, copy(central), central);
        checkedOut(child, copy(central), central);
        checkedOut(child, copy(central), central);
        assertThat(returnLoan(first, central.getId())).hasStatus(HttpStatus.OK);

        assertThat(checkout(child, copy(central), central)).hasStatus(HttpStatus.CREATED);
        assertThat(loans.countOpenByReader(child.getId())).isEqualTo(3);
    }

    @Test
    void limitOfTheCurrentCategory() throws Exception {
        Reader reader = reader("STUDENT", CHILD_BIRTH);
        for (int i = 0; i < 4; i++) {
            checkedOut(reader, copy(central), central);
        }
        assertThat(updateCategory(reader, "CHILD")).hasStatus(HttpStatus.OK);

        assertThat(checkout(reader, copy(central), central))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("LOAN_LIMIT_EXCEEDED");
        assertThat(loans.countOpenByReader(reader.getId())).isEqualTo(4);
    }

    // --- return a loan ---

    @Test
    void loanIsReturned() throws Exception {
        Copy copy = copy(central);
        long loanId = checkedOut(reader("ADULT", ADULT_BIRTH), copy, central);

        var body = assertThat(returnLoan(loanId, central.getId())).hasStatus(HttpStatus.OK).bodyJson();
        body.extractingPath("$.id").isEqualTo((int) loanId);
        body.extractingPath("$.status").isEqualTo("RETURNED");
        body.extractingPath("$.returnedAt").isEqualTo(NOW);
        body.extractingPath("$.overdue").isEqualTo(false);

        assertThat(mvc.get().uri("/api/copies/{id}", copy.getId()))
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.status").isEqualTo("ON_SHELF");
    }

    @Test
    void lateReturnIsAccepted() throws Exception {
        long loanId = checkedOut(reader("ADULT", ADULT_BIRTH), copy(central), central);
        setDueDate(loanId, LocalDate.of(2026, 10, 20));

        var body = assertThat(returnLoan(loanId, central.getId())).hasStatus(HttpStatus.OK).bodyJson();
        body.extractingPath("$.status").isEqualTo("RETURNED");
        body.extractingPath("$.overdue").isEqualTo(false);
    }

    @Test
    void loanAlreadyReturned() throws Exception {
        long loanId = checkedOut(reader("ADULT", ADULT_BIRTH), copy(central), central);
        assertThat(returnLoan(loanId, central.getId())).hasStatus(HttpStatus.OK);
        Object returnedAt = returnedAtOf(loanId);

        assertThat(returnLoan(loanId, central.getId()))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("LOAN_ALREADY_RETURNED");
        assertThat(returnedAtOf(loanId)).isEqualTo(returnedAt);
    }

    @Test
    void returnOfUnknownLoan() {
        assertThat(returnLoan(999L, central.getId()))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("LOAN_NOT_FOUND");
    }

    @Test
    void unknownReturnBranch() throws Exception {
        long loanId = checkedOut(reader("ADULT", ADULT_BIRTH), copy(central), central);

        assertThat(returnLoan(loanId, 999L))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("BRANCH_NOT_FOUND");
    }

    @Test
    void missingReturnBranch() throws Exception {
        long loanId = checkedOut(reader("ADULT", ADULT_BIRTH), copy(central), central);

        var body = assertThat(mvc.post().uri("/api/loans/{id}/return", loanId)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson();
        body.extractingPath("$.code").isEqualTo(ErrorCodes.VALIDATION_FAILED);
        body.extractingPath("$.errors[*].field").asArray().containsExactly("branchId");
    }

    // --- return only at the copy's branch ---

    @Test
    void returnAtAnotherBranch() throws Exception {
        Copy copy = copy(north);
        long loanId = checkedOut(reader("ADULT", ADULT_BIRTH), copy, north);

        assertThat(returnLoan(loanId, central.getId()))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo("LOAN_WRONG_BRANCH");
        assertThat(getLoan(loanId)).hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.status").isEqualTo("OPEN");
        assertThat(statusOf(copy)).isEqualTo(CopyStatus.ON_LOAN);
    }

    // --- overdue flag ---

    @Test
    void openLoanPastDueDateIsOverdue() throws Exception {
        long loanId = checkedOut(reader("ADULT", ADULT_BIRTH), copy(central), central);
        setDueDate(loanId, LocalDate.of(2026, 11, 3));

        assertThat(getLoan(loanId)).hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.overdue").isEqualTo(true);
    }

    @Test
    void openLoanDueTodayIsNotOverdue() throws Exception {
        long loanId = checkedOut(reader("ADULT", ADULT_BIRTH), copy(central), central);
        setDueDate(loanId, FixedClockConfiguration.TODAY);

        assertThat(getLoan(loanId)).hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.overdue").isEqualTo(false);
    }

    @Test
    void returnedLoanIsNeverOverdue() throws Exception {
        long loanId = checkedOut(reader("ADULT", ADULT_BIRTH), copy(central), central);
        assertThat(returnLoan(loanId, central.getId())).hasStatus(HttpStatus.OK);
        setDueDate(loanId, LocalDate.of(2026, 11, 1));

        var body = assertThat(getLoan(loanId)).hasStatus(HttpStatus.OK).bodyJson();
        body.extractingPath("$.status").isEqualTo("RETURNED");
        body.extractingPath("$.overdue").isEqualTo(false);
    }

    // --- get a loan ---

    @Test
    void getLoanById() throws Exception {
        Reader reader = reader("CHILD", CHILD_BIRTH);
        Copy copy = copy(north);
        long loanId = checkedOut(reader, copy, north);

        var body = assertThat(getLoan(loanId)).hasStatus(HttpStatus.OK).bodyJson();
        body.extractingPath("$.id").isEqualTo((int) loanId);
        body.extractingPath("$.reader.id").isEqualTo(reader.getId().intValue());
        body.extractingPath("$.reader.cardNumber").isEqualTo(reader.getCardNumber());
        body.extractingPath("$.copy.id").isEqualTo(copy.getId().intValue());
        body.extractingPath("$.copy.inventoryNumber").isEqualTo(copy.getInventoryNumber());
        body.extractingPath("$.copy.bookId").isEqualTo(book.getId().intValue());
        body.extractingPath("$.branch.id").isEqualTo(north.getId().intValue());
        body.extractingPath("$.branch.code").isEqualTo("NORTH");
        body.extractingPath("$.status").isEqualTo("OPEN");
        body.extractingPath("$.checkedOutAt").isEqualTo(NOW);
        body.extractingPath("$.dueDate").isEqualTo("2026-11-18");
        body.extractingPath("$.loanPeriodDays").isEqualTo(14);
        body.extractingPath("$.returnedAt").isNull();
        body.extractingPath("$.overdue").isEqualTo(false);
    }

    @Test
    void getUnknownLoan() {
        assertThat(getLoan(999L))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("LOAN_NOT_FOUND");
    }

    // --- list loans ---

    @Test
    void openLoansOfReader() throws Exception {
        Reader reader = reader("ADULT", ADULT_BIRTH);
        long returned = checkedOut(reader, copy(central), central);
        long open1 = checkedOut(reader, copy(central), central);
        long open2 = checkedOut(reader, copy(central), central);
        assertThat(returnLoan(returned, central.getId())).hasStatus(HttpStatus.OK);
        checkedOut(reader("ADULT", ADULT_BIRTH), copy(central), central);

        var body = assertThat(mvc.get().uri("/api/loans?readerId={id}&status=OPEN", reader.getId()))
                .hasStatus(HttpStatus.OK)
                .bodyJson();
        body.extractingPath("$.items[*].id").asArray().containsExactly((int) open2, (int) open1);
        body.extractingPath("$.items[*].status").asArray().containsOnly("OPEN");
        body.extractingPath("$.totalElements").isEqualTo(2);
    }

    @Test
    void returnedLoansOfReader() throws Exception {
        Reader reader = reader("ADULT", ADULT_BIRTH);
        long returned = checkedOut(reader, copy(central), central);
        checkedOut(reader, copy(central), central);
        assertThat(returnLoan(returned, central.getId())).hasStatus(HttpStatus.OK);

        var body = assertThat(mvc.get().uri("/api/loans?readerId={id}&status=RETURNED", reader.getId()))
                .hasStatus(HttpStatus.OK)
                .bodyJson();
        body.extractingPath("$.items[*].id").asArray().containsExactly((int) returned);
        body.extractingPath("$.totalElements").isEqualTo(1);
    }

    @Test
    void loanHistoryOfCopy() throws Exception {
        Copy copy = copy(central);
        Reader reader = reader("ADULT", ADULT_BIRTH);
        for (int i = 0; i < 2; i++) {
            long loanId = checkedOut(reader, copy, central);
            assertThat(returnLoan(loanId, central.getId())).hasStatus(HttpStatus.OK);
        }
        long open = checkedOut(reader, copy, central);
        checkedOut(reader, copy(central), central);

        var body = assertThat(mvc.get().uri("/api/loans?copyId={id}", copy.getId()))
                .hasStatus(HttpStatus.OK)
                .bodyJson();
        body.extractingPath("$.items").asArray().hasSize(3);
        body.extractingPath("$.items[0].id").isEqualTo((int) open);
        body.extractingPath("$.items[0].status").isEqualTo("OPEN");
        body.extractingPath("$.items[1].status").isEqualTo("RETURNED");
        body.extractingPath("$.items[2].status").isEqualTo("RETURNED");
        body.extractingPath("$.totalElements").isEqualTo(3);
    }

    @Test
    void paginatedLoanList() {
        Reader reader = reader("ADULT", ADULT_BIRTH);
        Instant now = Instant.parse(NOW);
        for (int i = 0; i < 25; i++) {
            loans.save(Loan.open(reader, copy(central), central, now, FixedClockConfiguration.TODAY, 21));
        }

        var body = assertThat(mvc.get().uri("/api/loans?page=2&size=10"))
                .hasStatus(HttpStatus.OK)
                .bodyJson();
        body.extractingPath("$.items").asArray().hasSize(5);
        body.extractingPath("$.page").isEqualTo(2);
        body.extractingPath("$.size").isEqualTo(10);
        body.extractingPath("$.totalElements").isEqualTo(25);
        body.extractingPath("$.totalPages").isEqualTo(3);
    }

    @Test
    void unknownStatusValue() {
        var body = assertThat(mvc.get().uri("/api/loans?status=OVERDUE"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson();
        body.extractingPath("$.code").isEqualTo(ErrorCodes.VALIDATION_FAILED);
        body.extractingPath("$.errors[*].field").asArray().containsExactly("status");
    }

    @Test
    void pageSizeOutOfRange() {
        assertThat(mvc.get().uri("/api/loans?size=0"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo(ErrorCodes.VALIDATION_FAILED);
    }

    // --- helpers ---

    private Reader reader(String category, LocalDate dateOfBirth) {
        cardSeq++;
        return readers.save(new Reader("LN-%06d".formatted(cardSeq), "Reader", "No" + cardSeq, dateOfBirth,
                category, "reader" + cardSeq + "@example.org", null));
    }

    private Copy copy(Branch branch) {
        return copies.save(new Copy(book, branch, nextInventoryNumber()));
    }

    private String nextInventoryNumber() {
        return "INV-%04d".formatted(++inventorySeq);
    }

    private MockMvcRequestBuilder checkout(Reader reader, Copy copy, Branch branch) {
        return checkout(reader.getId(), copy.getId(), branch.getId());
    }

    private MockMvcRequestBuilder checkout(Long readerId, Long copyId, Long branchId) {
        return mvc.post().uri("/api/loans").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"readerId": %d, "copyId": %d, "branchId": %d}""".formatted(readerId, copyId, branchId));
    }

    /** Checks out through the API and returns the new loan id. */
    private long checkedOut(Reader reader, Copy copy, Branch branch) throws Exception {
        var result = checkout(reader, copy, branch).exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private MockMvcRequestBuilder returnLoan(long loanId, long branchId) {
        return mvc.post().uri("/api/loans/{id}/return", loanId).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"branchId": %d}""".formatted(branchId));
    }

    private MockMvcRequestBuilder getLoan(long loanId) {
        return mvc.get().uri("/api/loans/{id}", loanId);
    }

    private MockMvcRequestBuilder updateCategory(Reader reader, String category) {
        return mvc.put().uri("/api/readers/{id}", reader.getId()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"firstName": "%s", "lastName": "%s", "dateOfBirth": "%s", "category": "%s",
                         "email": "%s"}""".formatted(reader.getFirstName(), reader.getLastName(),
                        reader.getDateOfBirth(), category, reader.getEmail()));
    }

    /** Past due dates cannot be reached through the API with a fixed clock, so set them directly. */
    private void setDueDate(long loanId, LocalDate dueDate) {
        jdbc.update("update loan set due_date = ? where id = ?", dueDate, loanId);
    }

    private Object returnedAtOf(long loanId) {
        return jdbc.queryForObject("select returned_at from loan where id = ?", Object.class, loanId);
    }

    private long loanCount() {
        return jdbc.queryForObject("select count(*) from loan", Long.class);
    }

    private CopyStatus statusOf(Copy copy) {
        return copies.findById(copy.getId()).orElseThrow().getStatus();
    }
}
