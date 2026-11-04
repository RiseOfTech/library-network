package io.github.riseoftech.library.loans;

import java.time.Instant;
import java.time.LocalDate;

import io.github.riseoftech.library.catalog.branch.Branch;
import io.github.riseoftech.library.catalog.copy.Copy;
import io.github.riseoftech.library.readers.reader.Reader;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A copy checked out to a reader. The loan period is stored at checkout, so later category or
 * configuration changes do not move the due date. Open while {@code returnedAt} is not set.
 */
@Entity
@Table(name = "loan")
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reader_id", nullable = false, updatable = false)
    private Reader reader;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "copy_id", nullable = false, updatable = false)
    private Copy copy;

    /** Branch where the checkout happened. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false, updatable = false)
    private Branch branch;

    @Column(name = "checked_out_at", nullable = false, updatable = false)
    private Instant checkedOutAt;

    @Column(name = "loan_period_days", nullable = false)
    private int loanPeriodDays;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @Version
    private long version;

    protected Loan() {
    }

    private Loan(Reader reader, Copy copy, Branch branch, Instant checkedOutAt, int loanPeriodDays,
            LocalDate dueDate) {
        this.reader = reader;
        this.copy = copy;
        this.branch = branch;
        this.checkedOutAt = checkedOutAt;
        this.loanPeriodDays = loanPeriodDays;
        this.dueDate = dueDate;
    }

    /**
     * A new open loan due {@code loanPeriodDays} after {@code today}.
     *
     * @param today the checkout date in the service's clock zone
     */
    public static Loan open(Reader reader, Copy copy, Branch branch, Instant checkedOutAt, LocalDate today,
            int loanPeriodDays) {
        if (loanPeriodDays <= 0) {
            throw new IllegalArgumentException("Loan period must be positive: " + loanPeriodDays);
        }
        return new Loan(reader, copy, branch, checkedOutAt, loanPeriodDays, today.plusDays(loanPeriodDays));
    }

    /** {@code OPEN -> RETURNED}. */
    public void returnAt(Instant returnedAt) {
        if (this.returnedAt != null) {
            throw new LoanAlreadyReturnedException(id);
        }
        this.returnedAt = returnedAt;
    }

    public LoanStatus status() {
        return returnedAt == null ? LoanStatus.OPEN : LoanStatus.RETURNED;
    }

    /** Open and due before {@code today}; a loan due today is not overdue yet. */
    public boolean isOverdue(LocalDate today) {
        return returnedAt == null && dueDate.isBefore(today);
    }

    public Long getId() {
        return id;
    }

    public Reader getReader() {
        return reader;
    }

    public Copy getCopy() {
        return copy;
    }

    public Branch getBranch() {
        return branch;
    }

    public Instant getCheckedOutAt() {
        return checkedOutAt;
    }

    public int getLoanPeriodDays() {
        return loanPeriodDays;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public Instant getReturnedAt() {
        return returnedAt;
    }
}
