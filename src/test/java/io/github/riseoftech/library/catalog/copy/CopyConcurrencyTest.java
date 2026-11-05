package io.github.riseoftech.library.catalog.copy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.github.riseoftech.library.IntegrationTest;
import io.github.riseoftech.library.catalog.book.Book;
import io.github.riseoftech.library.catalog.book.BookRepository;
import io.github.riseoftech.library.catalog.branch.Branch;
import io.github.riseoftech.library.catalog.branch.BranchRepository;
import io.github.riseoftech.library.common.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

class CopyConcurrencyTest extends IntegrationTest {

    private static final int ROUNDS = 10;

    @Autowired
    CopyRepository copies;

    @Autowired
    BookRepository books;

    @Autowired
    BranchRepository branches;

    @BeforeEach
    void cleanDatabase() {
        truncate("loan", "copy", "book_author", "book", "branch");
    }

    @Test
    void parallelMarkLostSucceedsExactlyOnce() throws Exception {
        Book book = books.save(new Book("Dune", List.of("Frank Herbert"), "9780441172719", null));
        Branch branch = branches.save(new Branch("CENTRAL", "Central Library", "1 Main St"));

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            for (int round = 0; round < ROUNDS; round++) {
                Copy copy = copies.save(new Copy(book, branch, "CEN-" + round));
                CountDownLatch start = new CountDownLatch(1);
                List<Future<MvcResult>> results = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    results.add(executor.submit(() -> {
                        start.await();
                        return mvc.post().uri("/api/copies/{id}/lost", copy.getId()).exchange().getMvcResult();
                    }));
                }
                start.countDown();

                List<Integer> statuses = new ArrayList<>();
                for (Future<MvcResult> result : results) {
                    MvcResult mvcResult = result.get();
                    statuses.add(mvcResult.getResponse().getStatus());
                    if (mvcResult.getResponse().getStatus() == 409) {
                        // Depending on timing the loser sees the committed LOST status or a stale version.
                        assertThat(mvcResult.getResponse().getContentAsString())
                                .containsAnyOf(CopyStatusConflictException.CODE, ErrorCodes.CONCURRENT_MODIFICATION);
                    }
                }

                assertThat(statuses).as("round %d", round).containsExactlyInAnyOrder(200, 409);
                assertThat(copies.findById(copy.getId()).orElseThrow().getStatus()).isEqualTo(CopyStatus.LOST);
            }
        }
    }
}
