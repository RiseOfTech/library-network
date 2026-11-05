package io.github.riseoftech.library.catalog.copy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import io.github.riseoftech.library.catalog.book.Book;
import io.github.riseoftech.library.catalog.branch.Branch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

class CopyTest {

    @Test
    void newCopyIsOnShelf() {
        assertThat(newCopy().getStatus()).isEqualTo(CopyStatus.ON_SHELF);
    }

    @Test
    void copyOnShelfCanBeMarkedLost() {
        Copy copy = copyIn(CopyStatus.ON_SHELF);

        copy.markLost();

        assertThat(copy.getStatus()).isEqualTo(CopyStatus.LOST);
    }

    @ParameterizedTest
    @EnumSource(value = CopyStatus.class, names = "ON_SHELF", mode = EnumSource.Mode.EXCLUDE)
    void copyNotOnShelfCannotBeMarkedLost(CopyStatus status) {
        Copy copy = copyIn(status);

        assertThatThrownBy(copy::markLost)
                .isInstanceOf(CopyStatusConflictException.class)
                .extracting("code").isEqualTo(CopyStatusConflictException.CODE);
        assertThat(copy.getStatus()).isEqualTo(status);
    }

    @Test
    void lostCopyCanBeMarkedFound() {
        Copy copy = copyIn(CopyStatus.LOST);

        copy.markFound();

        assertThat(copy.getStatus()).isEqualTo(CopyStatus.ON_SHELF);
    }

    @ParameterizedTest
    @EnumSource(value = CopyStatus.class, names = "LOST", mode = EnumSource.Mode.EXCLUDE)
    void copyNotLostCannotBeMarkedFound(CopyStatus status) {
        Copy copy = copyIn(status);

        assertThatThrownBy(copy::markFound)
                .isInstanceOf(CopyStatusConflictException.class)
                .extracting("code").isEqualTo(CopyStatusConflictException.CODE);
        assertThat(copy.getStatus()).isEqualTo(status);
    }

    @Test
    void copyOnShelfCanBeCheckedOut() {
        Copy copy = copyIn(CopyStatus.ON_SHELF);

        copy.checkOut();

        assertThat(copy.getStatus()).isEqualTo(CopyStatus.ON_LOAN);
    }

    @ParameterizedTest
    @EnumSource(value = CopyStatus.class, names = "ON_SHELF", mode = EnumSource.Mode.EXCLUDE)
    void copyNotOnShelfCannotBeCheckedOut(CopyStatus status) {
        Copy copy = copyIn(status);

        assertThatThrownBy(copy::checkOut)
                .isInstanceOf(CopyStatusConflictException.class)
                .extracting("code").isEqualTo(CopyStatusConflictException.CODE);
        assertThat(copy.getStatus()).isEqualTo(status);
    }

    @Test
    void copyOnLoanCanBeCheckedIn() {
        Copy copy = copyIn(CopyStatus.ON_LOAN);

        copy.checkIn();

        assertThat(copy.getStatus()).isEqualTo(CopyStatus.ON_SHELF);
    }

    @ParameterizedTest
    @EnumSource(value = CopyStatus.class, names = "ON_LOAN", mode = EnumSource.Mode.EXCLUDE)
    void copyNotOnLoanCannotBeCheckedIn(CopyStatus status) {
        Copy copy = copyIn(status);

        assertThatThrownBy(copy::checkIn)
                .isInstanceOf(CopyStatusConflictException.class)
                .extracting("code").isEqualTo(CopyStatusConflictException.CODE);
        assertThat(copy.getStatus()).isEqualTo(status);
    }

    private static Copy newCopy() {
        return new Copy(new Book("Dune", List.of("Frank Herbert"), "9780441172719", null),
                new Branch("CENTRAL", "Central", "1 Main St"), "CEN-1");
    }

    /** Statuses other than ON_SHELF/LOST are reached through later changes, so set them directly. */
    private static Copy copyIn(CopyStatus status) {
        Copy copy = newCopy();
        ReflectionTestUtils.setField(copy, "status", status);
        return copy;
    }
}
