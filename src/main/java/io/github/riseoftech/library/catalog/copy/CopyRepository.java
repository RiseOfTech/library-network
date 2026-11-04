package io.github.riseoftech.library.catalog.copy;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CopyRepository extends JpaRepository<Copy, Long> {

    boolean existsByInventoryNumber(String inventoryNumber);

    /** Every filter is optional: {@code null} means "any". */
    @Query("""
            select c from Copy c
            where (:bookId is null or c.book.id = :bookId)
              and (:branchId is null or c.branch.id = :branchId)
              and (:status is null or c.status = :status)
            """)
    Page<Copy> findFiltered(Long bookId, Long branchId, CopyStatus status, Pageable pageable);

    @Query("""
            select new io.github.riseoftech.library.catalog.copy.BranchAvailability(
                br.id, br.code,
                sum(case when c.status <> :lost then 1 else 0 end),
                sum(case when c.status = :onShelf then 1 else 0 end))
            from Copy c join c.branch br
            where c.book.id = :bookId
            group by br.id, br.code
            order by br.code
            """)
    List<BranchAvailability> availability(long bookId, CopyStatus lost, CopyStatus onShelf);

    default List<BranchAvailability> availability(long bookId) {
        return availability(bookId, CopyStatus.LOST, CopyStatus.ON_SHELF);
    }
}
