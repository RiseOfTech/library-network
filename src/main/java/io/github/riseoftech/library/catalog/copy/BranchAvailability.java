package io.github.riseoftech.library.catalog.copy;

/**
 * Copies of one book in one branch: {@code total} excludes lost copies, {@code onShelf} counts
 * copies available right now.
 */
public record BranchAvailability(long branchId, String branchCode, long total, long onShelf) {
}
