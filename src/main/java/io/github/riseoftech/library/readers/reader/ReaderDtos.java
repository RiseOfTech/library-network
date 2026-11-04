package io.github.riseoftech.library.readers.reader;

import java.time.LocalDate;

import io.github.riseoftech.library.readers.category.KnownCategory;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class ReaderDtos {

    private ReaderDtos() {
    }

    /**
     * Used for both registration and full update. Has no {@code cardNumber}: a value sent by the
     * client is ignored. A blank {@code email} or {@code phone} means "not given".
     */
    @ContactRequired
    public record ReaderRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotNull @PastOrPresent LocalDate dateOfBirth,
            @NotNull @KnownCategory String category,
            @Email @Size(max = 254) String email,
            @Pattern(regexp = "(\\+?[0-9][0-9 ()-]{4,19})?", message = "must be a phone number")
            @Size(max = 20) String phone) {
    }

    public record ReaderResponse(long id, String cardNumber, String firstName, String lastName,
            LocalDate dateOfBirth, String category, String email, String phone) {

        static ReaderResponse from(Reader reader) {
            return new ReaderResponse(reader.getId(), reader.getCardNumber(), reader.getFirstName(),
                    reader.getLastName(), reader.getDateOfBirth(), reader.getCategory(), reader.getEmail(),
                    reader.getPhone());
        }
    }
}
