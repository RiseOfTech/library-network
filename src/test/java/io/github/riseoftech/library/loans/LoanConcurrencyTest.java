package io.github.riseoftech.library.loans;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.jayway.jsonpath.JsonPath;
import io.github.riseoftech.library.IntegrationTest;
import io.github.riseoftech.library.catalog.book.Book;
import io.github.riseoftech.library.catalog.book.BookRepository;
import io.github.riseoftech.library.catalog.branch.Branch;
import io.github.riseoftech.library.catalog.branch.BranchRepository;
import io.github.riseoftech.library.catalog.copy.Copy;
import io.github.riseoftech.library.catalog.copy.CopyRepository;
import io.github.riseoftech.library.catalog.copy.CopyStatus;
import io.github.riseoftech.library.catalog.copy.CopyStatusConflictException;
import io.github.riseoftech.library.common.ErrorCodes;
import io.github.riseoftech.library.readers.reader.Reader;
import io.github.riseoftech.library.readers.reader.ReaderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Parallel requests started together by a latch, repeated for several rounds to vary the timing.
 */
class LoanConcurrencyTest extends IntegrationTest {

    private static final int ROUNDS = 10;

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
    Branch branch;
    int cardSeq;
    int inventorySeq;

    @BeforeEach
    void setUp() {
        truncate("loan", "copy", "book_author", "book", "branch", "reader");
        book = books.save(new Book("Dune", List.of("Frank Herbert"), "9780441172719", null));
        branch = branches.save(new Branch("CENTRAL", "Central Library", "1 Main St"));
    }

    @Test
    void twoReadersCheckOutOneCopy() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            Copy copy = copy();
            Reader first = reader("ADULT", LocalDate.of(1990, 5, 17));
            Reader second = reader("ADULT", LocalDate.of(1985, 1, 2));

            List<MvcResult> results = race(
                    () -> checkout(first, copy),
                    () -> checkout(second, copy));

            assertThat(statuses(results)).as("round %d", round).containsExactlyInAnyOrder(201, 409);
            assertThat(loserBody(results)).as("round %d", round).contains(CopyNotAvailableException.CODE);
            assertThat(openLoansOfCopy(copy)).as("round %d", round).isEqualTo(1);
            assertThat(statusOf(copy)).isEqualTo(CopyStatus.ON_LOAN);
        }
    }

    @Test
    void oneReaderChecksOutSeveralCopiesAtTheLimit() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            Reader child = reader("CHILD", LocalDate.of(2016, 3, 1));
            for (int i = 0; i < 2; i++) {
                assertThat(checkout(child, copy()).getResponse().getStatus()).isEqualTo(201);
            }
            List<Copy> candidates = List.of(copy(), copy(), copy());

            List<MvcResult> results = race(
                    () -> checkout(child, candidates.get(0)),
                    () -> checkout(child, candidates.get(1)),
                    () -> checkout(child, candidates.get(2)));

            assertThat(statuses(results)).as("round %d", round).containsExactlyInAnyOrder(201, 409, 409);
            for (int i = 0; i < results.size(); i++) {
                MvcResult result = results.get(i);
                if (result.getResponse().getStatus() == 409) {
                    assertThat(result.getResponse().getContentAsString())
                            .contains(LoanLimitExceededException.CODE);
                    assertThat(statusOf(candidates.get(i))).isEqualTo(CopyStatus.ON_SHELF);
                } else {
                    assertThat(statusOf(candidates.get(i))).isEqualTo(CopyStatus.ON_LOAN);
                }
            }
            assertThat(loans.countOpenByReader(child.getId())).as("round %d", round).isEqualTo(3);
        }
    }

    @Test
    void twoReturnsOfOneLoan() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            Copy copy = copy();
            MvcResult checkout = checkout(reader("ADULT", LocalDate.of(1990, 5, 17)), copy);
            assertThat(checkout.getResponse().getStatus()).isEqualTo(201);
            long loanId = ((Number) JsonPath.read(checkout.getResponse().getContentAsString(), "$.id")).longValue();

            List<MvcResult> results = race(
                    () -> returnLoan(loanId),
                    () -> returnLoan(loanId));

            assertThat(statuses(results)).as("round %d", round).containsExactlyInAnyOrder(200, 409);
            assertThat(loserBody(results)).as("round %d", round).contains(LoanAlreadyReturnedException.CODE);
            assertThat(statusOf(copy)).isEqualTo(CopyStatus.ON_SHELF);
            assertThat(openLoansOfCopy(copy)).isZero();
        }
    }

    @Test
    void checkoutAgainstMarkLost() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            Copy copy = copy();
            Reader reader = reader("ADULT", LocalDate.of(1990, 5, 17));

            List<MvcResult> results = race(
                    () -> checkout(reader, copy),
                    () -> mvc.post().uri("/api/copies/{id}/lost", copy.getId()).exchange().getMvcResult());
            int checkoutStatus = results.get(0).getResponse().getStatus();
            int lostStatus = results.get(1).getResponse().getStatus();

            boolean checkoutWon = checkoutStatus == 201;
            boolean lostWon = lostStatus == 200;
            assertThat(checkoutWon ^ lostWon)
                    .as("round %d: checkout %d, lost %d", round, checkoutStatus, lostStatus).isTrue();
            MvcResult loser = checkoutWon ? results.get(1) : results.get(0);
            assertThat(loser.getResponse().getStatus()).isEqualTo(409);
            assertThat(loser.getResponse().getContentAsString()).containsAnyOf(CopyNotAvailableException.CODE,
                    CopyStatusConflictException.CODE, ErrorCodes.CONCURRENT_MODIFICATION);
            assertThat(statusOf(copy)).as("round %d", round)
                    .isEqualTo(checkoutWon ? CopyStatus.ON_LOAN : CopyStatus.LOST);
            assertThat(openLoansOfCopy(copy)).isEqualTo(checkoutWon ? 1 : 0);
        }
    }

    // --- helpers ---

    /** Runs all requests at once and returns their results in the order given. */
    @SafeVarargs
    private List<MvcResult> race(Callable<MvcResult>... requests) throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(requests.length)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (Callable<MvcResult> request : requests) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return request.call();
                }));
            }
            start.countDown();
            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                results.add(future.get());
            }
            return results;
        }
    }

    private static List<Integer> statuses(List<MvcResult> results) {
        return results.stream().map(r -> r.getResponse().getStatus()).toList();
    }

    private static String loserBody(List<MvcResult> results) throws Exception {
        for (MvcResult result : results) {
            if (result.getResponse().getStatus() == 409) {
                return result.getResponse().getContentAsString();
            }
        }
        return "";
    }

    private MvcResult checkout(Reader reader, Copy copy) {
        return mvc.post().uri("/api/loans").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"readerId": %d, "copyId": %d, "branchId": %d}"""
                        .formatted(reader.getId(), copy.getId(), branch.getId()))
                .exchange().getMvcResult();
    }

    private MvcResult returnLoan(long loanId) {
        return mvc.post().uri("/api/loans/{id}/return", loanId).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"branchId": %d}""".formatted(branch.getId()))
                .exchange().getMvcResult();
    }

    private Reader reader(String category, LocalDate dateOfBirth) {
        cardSeq++;
        return readers.save(new Reader("LN-%06d".formatted(cardSeq), "Reader", "No" + cardSeq, dateOfBirth,
                category, "reader" + cardSeq + "@example.org", null));
    }

    private Copy copy() {
        return copies.save(new Copy(book, branch, "INV-%04d".formatted(++inventorySeq)));
    }

    private long openLoansOfCopy(Copy copy) {
        return jdbc.queryForObject("select count(*) from loan where copy_id = ? and returned_at is null",
                Long.class, copy.getId());
    }

    private CopyStatus statusOf(Copy copy) {
        return copies.findById(copy.getId()).orElseThrow().getStatus();
    }
}
