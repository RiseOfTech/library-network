package io.github.riseoftech.library.readers.reader;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ReaderRepository extends JpaRepository<Reader, Long> {

    /** {@code select ... for update}: the row stays locked until the transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reader r where r.id = :id")
    Optional<Reader> findByIdForUpdate(long id);

    /** Next value of {@code reader_card_number_seq}; never returns the same value twice. */
    @Query(value = "select nextval('reader_card_number_seq')", nativeQuery = true)
    long nextCardNumber();

    /** Exact card number match; {@code category} {@code null} means any category. */
    @Query("""
            select r from Reader r
            where r.cardNumber = :cardNumber
              and (:category is null or r.category = :category)
            """)
    Page<Reader> findByCardNumber(String cardNumber, String category, Pageable pageable);

    /**
     * Case-insensitive substring match on the first name, last name or "first last" full name.
     * {@code pattern} is already lower-cased, wrapped in {@code %} and escaped with {@code \};
     * {@code null} means no name filter. {@code category} {@code null} means any category.
     */
    @Query("""
            select r from Reader r
            where (:pattern is null
                   or lower(r.firstName) like :pattern escape '\\'
                   or lower(r.lastName) like :pattern escape '\\'
                   or lower(concat(r.firstName, ' ', r.lastName)) like :pattern escape '\\')
              and (:category is null or r.category = :category)
            """)
    Page<Reader> search(String pattern, String category, Pageable pageable);
}
