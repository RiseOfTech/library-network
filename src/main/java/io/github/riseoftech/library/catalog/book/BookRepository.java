package io.github.riseoftech.library.catalog.book;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BookRepository extends JpaRepository<Book, Long> {

    boolean existsByIsbn(String isbn);

    boolean existsByIsbnAndIdNot(String isbn, Long id);

    Page<Book> findByIsbn(String isbn, Pageable pageable);

    /**
     * Case-insensitive substring match on title or any author name.
     * {@code pattern} is already lower-cased, wrapped in {@code %} and escaped with {@code \}.
     */
    @Query(value = """
            select distinct b from Book b left join b.authors a
            where lower(b.title) like :pattern escape '\\'
               or lower(a) like :pattern escape '\\'
            """,
            countQuery = """
            select count(distinct b) from Book b left join b.authors a
            where lower(b.title) like :pattern escape '\\'
               or lower(a) like :pattern escape '\\'
            """)
    Page<Book> searchByTitleOrAuthor(String pattern, Pageable pageable);
}
