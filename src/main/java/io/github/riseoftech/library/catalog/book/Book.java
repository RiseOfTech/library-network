package io.github.riseoftech.library.catalog.book;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "book")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;

    @ElementCollection
    @CollectionTable(name = "book_author", joinColumns = @JoinColumn(name = "book_id"))
    @OrderColumn(name = "position")
    @Column(name = "name", nullable = false, length = 200)
    @BatchSize(size = 100)
    private List<String> authors = new ArrayList<>();

    /** 13 digits, normalized. */
    @Column(nullable = false, length = 13)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String isbn;

    @Column(name = "publication_year")
    private Integer publicationYear;

    protected Book() {
    }

    public Book(String title, List<String> authors, String isbn, Integer publicationYear) {
        replace(title, authors, isbn, publicationYear);
    }

    public void replace(String title, List<String> authors, String isbn, Integer publicationYear) {
        this.title = title;
        this.authors.clear();
        this.authors.addAll(authors);
        this.isbn = isbn;
        this.publicationYear = publicationYear;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getAuthors() {
        return List.copyOf(authors);
    }

    public String getIsbn() {
        return isbn;
    }

    public Integer getPublicationYear() {
        return publicationYear;
    }
}
