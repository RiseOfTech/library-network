package io.github.riseoftech.library.catalog.copy;

import io.github.riseoftech.library.catalog.book.Book;
import io.github.riseoftech.library.catalog.branch.Branch;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A physical copy of a book held by a branch. Status changes only through behavior methods.
 */
@Entity
@Table(name = "copy")
public class Copy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false, updatable = false)
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(name = "inventory_number", nullable = false, updatable = false, length = 30)
    private String inventoryNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CopyStatus status;

    @Version
    private long version;

    protected Copy() {
    }

    public Copy(Book book, Branch branch, String inventoryNumber) {
        this.book = book;
        this.branch = branch;
        this.inventoryNumber = inventoryNumber;
        this.status = CopyStatus.ON_SHELF;
    }

    /** {@code ON_SHELF -> LOST}. */
    public void markLost() {
        requireStatus(CopyStatus.ON_SHELF, "marked lost");
        status = CopyStatus.LOST;
    }

    /** {@code LOST -> ON_SHELF}, back in the branch the copy belongs to. */
    public void markFound() {
        requireStatus(CopyStatus.LOST, "marked found");
        status = CopyStatus.ON_SHELF;
    }

    /** {@code ON_SHELF -> ON_LOAN}. */
    public void checkOut() {
        requireStatus(CopyStatus.ON_SHELF, "checked out");
        status = CopyStatus.ON_LOAN;
    }

    /** {@code ON_LOAN -> ON_SHELF}, back in the branch the copy belongs to. */
    public void checkIn() {
        requireStatus(CopyStatus.ON_LOAN, "checked in");
        status = CopyStatus.ON_SHELF;
    }

    private void requireStatus(CopyStatus expected, String action) {
        if (status != expected) {
            throw new CopyStatusConflictException(id, status, action);
        }
    }

    public Long getId() {
        return id;
    }

    public Book getBook() {
        return book;
    }

    public Branch getBranch() {
        return branch;
    }

    public String getInventoryNumber() {
        return inventoryNumber;
    }

    public CopyStatus getStatus() {
        return status;
    }
}
