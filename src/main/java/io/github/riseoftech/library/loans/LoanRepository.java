package io.github.riseoftech.library.loans;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    /** {@code select ... for update}: the row stays locked until the transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Loan l where l.id = :id")
    Optional<Loan> findByIdForUpdate(long id);

    @Query("select count(l) from Loan l where l.reader.id = :readerId and l.returnedAt is null")
    long countOpenByReader(long readerId);

    /**
     * Every filter is optional: {@code null} means "any". {@code open} {@code true} means open loans,
     * {@code false} returned ones.
     */
    @Query(value = """
            select l from Loan l
            join fetch l.reader r join fetch l.copy c join fetch l.branch
            where (:readerId is null or r.id = :readerId)
              and (:copyId is null or c.id = :copyId)
              and (:open is null
                   or (:open = true and l.returnedAt is null)
                   or (:open = false and l.returnedAt is not null))
            """,
            countQuery = """
            select count(l) from Loan l
            where (:readerId is null or l.reader.id = :readerId)
              and (:copyId is null or l.copy.id = :copyId)
              and (:open is null
                   or (:open = true and l.returnedAt is null)
                   or (:open = false and l.returnedAt is not null))
            """)
    Page<Loan> findFiltered(Long readerId, Long copyId, Boolean open, Pageable pageable);
}
