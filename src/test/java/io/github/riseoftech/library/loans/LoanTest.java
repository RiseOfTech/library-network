package io.github.riseoftech.library.loans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import io.github.riseoftech.library.catalog.book.Book;
import io.github.riseoftech.library.catalog.branch.Branch;
import io.github.riseoftech.library.catalog.copy.Copy;
import io.github.riseoftech.library.readers.reader.Reader;
import org.junit.jupiter.api.Test;

class LoanTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);
    private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");

    @Test
    void dueDateForTwentyOneDaysCrossesMonthBoundary() {
        Loan loan = loan(LocalDate.of(2026, 11, 20), 21);

        assertThat(loan.getDueDate()).isEqualTo(LocalDate.of(2026, 12, 11));
        assertThat(loan.getLoanPeriodDays()).isEqualTo(21);
    }

    @Test
    void dueDateForFourteenDaysCrossesMonthBoundary() {
        Loan loan = loan(LocalDate.of(2026, 11, 25), 14);

        assertThat(loan.getDueDate()).isEqualTo(LocalDate.of(2026, 12, 9));
        assertThat(loan.getLoanPeriodDays()).isEqualTo(14);
    }

    @Test
    void periodMustBePositive() {
        assertThatThrownBy(() -> loan(TODAY, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void newLoanIsOpen() {
        Loan loan = loan(TODAY, 21);

        assertThat(loan.status()).isEqualTo(LoanStatus.OPEN);
        assertThat(loan.getReturnedAt()).isNull();
        assertThat(loan.getCheckedOutAt()).isEqualTo(NOW);
    }

    @Test
    void returnedLoanIsReturned() {
        Loan loan = loan(TODAY, 21);
        Instant returnedAt = NOW.plusSeconds(3600);

        loan.returnAt(returnedAt);

        assertThat(loan.status()).isEqualTo(LoanStatus.RETURNED);
        assertThat(loan.getReturnedAt()).isEqualTo(returnedAt);
    }

    @Test
    void secondReturnIsRejected() {
        Loan loan = loan(TODAY, 21);
        Instant returnedAt = NOW.plusSeconds(3600);
        loan.returnAt(returnedAt);

        assertThatThrownBy(() -> loan.returnAt(NOW.plusSeconds(7200)))
                .isInstanceOf(LoanAlreadyReturnedException.class)
                .extracting("code").isEqualTo(LoanAlreadyReturnedException.CODE);
        assertThat(loan.getReturnedAt()).isEqualTo(returnedAt);
    }

    @Test
    void openLoanDueYesterdayIsOverdue() {
        Loan loan = loan(TODAY.minusDays(15), 14);

        assertThat(loan.getDueDate()).isEqualTo(TODAY.minusDays(1));
        assertThat(loan.isOverdue(TODAY)).isTrue();
    }

    @Test
    void openLoanDueTodayIsNotOverdue() {
        Loan loan = loan(TODAY.minusDays(14), 14);

        assertThat(loan.getDueDate()).isEqualTo(TODAY);
        assertThat(loan.isOverdue(TODAY)).isFalse();
    }

    @Test
    void returnedLoanIsNeverOverdue() {
        Loan loan = loan(TODAY.minusDays(30), 14);
        loan.returnAt(NOW);

        assertThat(loan.isOverdue(TODAY)).isFalse();
    }

    private static Loan loan(LocalDate checkoutDate, int periodDays) {
        Branch branch = new Branch("CENTRAL", "Central", "1 Main St");
        Copy copy = new Copy(new Book("Dune", List.of("Frank Herbert"), "9780441172719", null), branch, "CEN-1");
        Reader reader = new Reader("LN-000001", "Anna", "Petrova", LocalDate.of(1990, 5, 17), "ADULT",
                "anna@example.org", null);
        return Loan.open(reader, copy, branch, NOW, checkoutDate, periodDays);
    }
}
