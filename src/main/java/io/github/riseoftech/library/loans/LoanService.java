package io.github.riseoftech.library.loans;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import io.github.riseoftech.library.catalog.branch.Branch;
import io.github.riseoftech.library.catalog.branch.BranchNotFoundException;
import io.github.riseoftech.library.catalog.branch.BranchRepository;
import io.github.riseoftech.library.catalog.copy.Copy;
import io.github.riseoftech.library.catalog.copy.CopyNotFoundException;
import io.github.riseoftech.library.catalog.copy.CopyRepository;
import io.github.riseoftech.library.catalog.copy.CopyStatus;
import io.github.riseoftech.library.common.PageResponse;
import io.github.riseoftech.library.common.UniqueConstraints;
import io.github.riseoftech.library.loans.LoanDtos.CheckoutRequest;
import io.github.riseoftech.library.loans.LoanDtos.LoanResponse;
import io.github.riseoftech.library.loans.LoanDtos.ReturnRequest;
import io.github.riseoftech.library.readers.category.ReaderCategories;
import io.github.riseoftech.library.readers.category.ReaderCategory;
import io.github.riseoftech.library.readers.reader.Reader;
import io.github.riseoftech.library.readers.reader.ReaderCategoryNotAllowedException;
import io.github.riseoftech.library.readers.reader.ReaderNotFoundException;
import io.github.riseoftech.library.readers.reader.ReaderRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checkout and return. Rows are locked with {@code select ... for update} before anything about them
 * is read: reader then copy on checkout, loan then copy on return. The reader lock makes the
 * count-then-insert of the loan limit safe; the copy lock serializes checkouts of one copy.
 */
@Service
@Transactional(readOnly = true)
public class LoanService {

    static final String OPEN_COPY_CONSTRAINT = "uk_loan_open_copy";

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("checkedOutAt"), Sort.Order.desc("id"));

    private final LoanRepository loans;
    private final ReaderRepository readers;
    private final CopyRepository copies;
    private final BranchRepository branches;
    private final ReaderCategories categories;
    private final Clock clock;

    public LoanService(LoanRepository loans, ReaderRepository readers, CopyRepository copies,
            BranchRepository branches, ReaderCategories categories, Clock clock) {
        this.loans = loans;
        this.readers = readers;
        this.copies = copies;
        this.branches = branches;
        this.categories = categories;
        this.clock = clock;
    }

    @Transactional
    public LoanResponse checkout(CheckoutRequest request) {
        Reader reader = readers.findByIdForUpdate(request.readerId())
                .orElseThrow(() -> new ReaderNotFoundException(request.readerId()));
        Copy copy = copies.findByIdForUpdate(request.copyId())
                .orElseThrow(() -> new CopyNotFoundException(request.copyId()));
        Branch branch = loadBranch(request.branchId());
        ReaderCategory category = categories.find(reader.getCategory())
                .orElseThrow(() -> new ReaderCategoryNotAllowedException(reader.getId(), reader.getCategory()));
        requireCopyBranch(copy, branch, "checked out");
        if (copy.getStatus() != CopyStatus.ON_SHELF) {
            throw new CopyNotAvailableException(copy.getId(), copy.getStatus());
        }
        if (loans.countOpenByReader(reader.getId()) >= category.loanLimit()) {
            throw new LoanLimitExceededException(reader.getId(), category.code(), category.loanLimit());
        }

        Loan loan = Loan.open(reader, copy, branch, Instant.now(clock), today(), category.loanPeriodDays());
        copy.checkOut();
        try {
            return LoanResponse.from(loans.saveAndFlush(loan), today());
        } catch (DataIntegrityViolationException ex) {
            if (UniqueConstraints.isViolated(ex, OPEN_COPY_CONSTRAINT)) {
                throw new CopyNotAvailableException(copy.getId());
            }
            throw ex;
        }
    }

    @Transactional
    public LoanResponse returnLoan(long id, ReturnRequest request) {
        Loan loan = loans.findByIdForUpdate(id).orElseThrow(() -> new LoanNotFoundException(id));
        Branch branch = loadBranch(request.branchId());
        if (loan.status() != LoanStatus.OPEN) {
            throw new LoanAlreadyReturnedException(loan.getId());
        }
        long copyId = loan.getCopy().getId();
        Copy copy = copies.findByIdForUpdate(copyId).orElseThrow(() -> new CopyNotFoundException(copyId));
        requireCopyBranch(copy, branch, "returned");

        copy.checkIn();
        loan.returnAt(Instant.now(clock));
        return LoanResponse.from(loans.saveAndFlush(loan), today());
    }

    public LoanResponse get(long id) {
        return LoanResponse.from(loans.findById(id).orElseThrow(() -> new LoanNotFoundException(id)), today());
    }

    /** Every filter is optional: {@code null} means "any". Newest checkout first. */
    public PageResponse<LoanResponse> list(Long readerId, Long copyId, LoanStatus status, int page, int size) {
        Boolean open = status == null ? null : status == LoanStatus.OPEN;
        LocalDate today = today();
        return PageResponse.from(
                loans.findFiltered(readerId, copyId, open, PageRequest.of(page, size, NEWEST_FIRST)),
                loan -> LoanResponse.from(loan, today));
    }

    private Branch loadBranch(long id) {
        return branches.findById(id).orElseThrow(() -> new BranchNotFoundException(id));
    }

    private static void requireCopyBranch(Copy copy, Branch branch, String action) {
        long copyBranchId = copy.getBranch().getId();
        if (copyBranchId != branch.getId()) {
            throw new LoanWrongBranchException(copy.getId(), copyBranchId, branch.getId(), action);
        }
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }
}
