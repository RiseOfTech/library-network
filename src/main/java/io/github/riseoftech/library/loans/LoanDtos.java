package io.github.riseoftech.library.loans;

import java.time.Instant;
import java.time.LocalDate;

import io.github.riseoftech.library.catalog.branch.Branch;
import io.github.riseoftech.library.catalog.copy.Copy;
import io.github.riseoftech.library.readers.reader.Reader;
import jakarta.validation.constraints.NotNull;

public final class LoanDtos {

    private LoanDtos() {
    }

    /** {@code branchId} is the branch where the checkout happens. */
    public record CheckoutRequest(@NotNull Long readerId, @NotNull Long copyId, @NotNull Long branchId) {
    }

    /** {@code branchId} is the branch where the return happens. */
    public record ReturnRequest(@NotNull Long branchId) {
    }

    public record ReaderSummary(long id, String cardNumber) {

        static ReaderSummary from(Reader reader) {
            return new ReaderSummary(reader.getId(), reader.getCardNumber());
        }
    }

    public record CopySummary(long id, String inventoryNumber, long bookId) {

        static CopySummary from(Copy copy) {
            return new CopySummary(copy.getId(), copy.getInventoryNumber(), copy.getBook().getId());
        }
    }

    public record BranchSummary(long id, String code) {

        static BranchSummary from(Branch branch) {
            return new BranchSummary(branch.getId(), branch.getCode());
        }
    }

    /**
     * {@code branch} is where the checkout happened; {@code overdue} is computed on read: open and
     * {@code dueDate} before today.
     */
    public record LoanResponse(long id, ReaderSummary reader, CopySummary copy, BranchSummary branch,
            LoanStatus status, Instant checkedOutAt, LocalDate dueDate, int loanPeriodDays, Instant returnedAt,
            boolean overdue) {

        static LoanResponse from(Loan loan, LocalDate today) {
            return new LoanResponse(loan.getId(), ReaderSummary.from(loan.getReader()),
                    CopySummary.from(loan.getCopy()), BranchSummary.from(loan.getBranch()), loan.status(),
                    loan.getCheckedOutAt(), loan.getDueDate(), loan.getLoanPeriodDays(), loan.getReturnedAt(),
                    loan.isOverdue(today));
        }
    }
}
