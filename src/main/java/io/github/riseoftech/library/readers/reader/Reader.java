package io.github.riseoftech.library.readers.reader;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A reader of the library network with one network-wide card. The card number never changes.
 */
@Entity
@Table(name = "reader")
public class Reader {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_number", nullable = false, updatable = false, length = 20)
    private String cardNumber;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    /** Upper-case code of a configured reader category. */
    @Column(nullable = false, length = 20)
    private String category;

    @Column(length = 254)
    private String email;

    @Column(length = 20)
    private String phone;

    @Version
    private long version;

    protected Reader() {
    }

    public Reader(String cardNumber, String firstName, String lastName, LocalDate dateOfBirth, String category,
            String email, String phone) {
        this.cardNumber = cardNumber;
        replace(firstName, lastName, dateOfBirth, category, email, phone);
    }

    /** Replaces everything except the card number. */
    public void replace(String firstName, String lastName, LocalDate dateOfBirth, String category,
            String email, String phone) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.dateOfBirth = dateOfBirth;
        this.category = category;
        this.email = email;
        this.phone = phone;
    }

    public Long getId() {
        return id;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public String getCategory() {
        return category;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }
}
