package io.github.riseoftech.library.catalog.copy;

import java.util.List;

import io.github.riseoftech.library.catalog.book.Book;
import io.github.riseoftech.library.catalog.book.BookNotFoundException;
import io.github.riseoftech.library.catalog.book.BookRepository;
import io.github.riseoftech.library.catalog.branch.Branch;
import io.github.riseoftech.library.catalog.branch.BranchNotFoundException;
import io.github.riseoftech.library.catalog.branch.BranchRepository;
import io.github.riseoftech.library.catalog.copy.CopyDtos.CopyResponse;
import io.github.riseoftech.library.catalog.copy.CopyDtos.RegisterCopyRequest;
import io.github.riseoftech.library.common.PageResponse;
import io.github.riseoftech.library.common.UniqueConstraints;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CopyService {

    static final String INVENTORY_NUMBER_CONSTRAINT = "uk_copy_inventory_number";

    private static final Sort BY_INVENTORY_NUMBER = Sort.by("inventoryNumber");

    private final CopyRepository copies;
    private final BookRepository books;
    private final BranchRepository branches;

    public CopyService(CopyRepository copies, BookRepository books, BranchRepository branches) {
        this.copies = copies;
        this.books = books;
        this.branches = branches;
    }

    @Transactional
    public CopyResponse register(RegisterCopyRequest request) {
        Book book = books.findById(request.bookId())
                .orElseThrow(() -> new BookNotFoundException(request.bookId()));
        Branch branch = branches.findById(request.branchId())
                .orElseThrow(() -> new BranchNotFoundException(request.branchId()));
        if (copies.existsByInventoryNumber(request.inventoryNumber())) {
            throw new CopyInventoryNumberTakenException(request.inventoryNumber());
        }
        try {
            return CopyResponse.from(copies.saveAndFlush(new Copy(book, branch, request.inventoryNumber())));
        } catch (DataIntegrityViolationException ex) {
            if (UniqueConstraints.isViolated(ex, INVENTORY_NUMBER_CONSTRAINT)) {
                throw new CopyInventoryNumberTakenException(request.inventoryNumber());
            }
            throw ex;
        }
    }

    public CopyResponse get(long id) {
        return CopyResponse.from(load(id));
    }

    public PageResponse<CopyResponse> list(Long bookId, Long branchId, CopyStatus status, int page, int size) {
        return PageResponse.from(
                copies.findFiltered(bookId, branchId, status, PageRequest.of(page, size, BY_INVENTORY_NUMBER)),
                CopyResponse::from);
    }

    @Transactional
    public CopyResponse markLost(long id) {
        Copy copy = load(id);
        copy.markLost();
        return CopyResponse.from(copies.saveAndFlush(copy));
    }

    @Transactional
    public CopyResponse markFound(long id) {
        Copy copy = load(id);
        copy.markFound();
        return CopyResponse.from(copies.saveAndFlush(copy));
    }

    public List<BranchAvailability> availability(long bookId) {
        if (!books.existsById(bookId)) {
            throw new BookNotFoundException(bookId);
        }
        return copies.availability(bookId);
    }

    private Copy load(long id) {
        return copies.findById(id).orElseThrow(() -> new CopyNotFoundException(id));
    }
}
