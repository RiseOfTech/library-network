package io.github.riseoftech.library.readers.reader;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReaderServiceTest {

    @Test
    void cardNumberIsZeroPaddedToSixDigits() {
        assertThat(ReaderService.formatCardNumber(1)).isEqualTo("LN-000001");
        assertThat(ReaderService.formatCardNumber(999_999)).isEqualTo("LN-999999");
    }

    @Test
    void cardNumberGrowsPastSixDigits() {
        assertThat(ReaderService.formatCardNumber(1_234_567)).isEqualTo("LN-1234567");
    }

    @ParameterizedTest
    @ValueSource(strings = {"LN-000042", "ln-000042", "Ln-1", "LN-1234567"})
    void cardNumberFormIsDetected(String query) {
        assertThat(ReaderService.isCardNumber(query)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "LN-", "LN000042", "LN-00a042", "xLN-000042", "LN-000042 x", "petrova"})
    void otherQueriesAreNotCardNumbers(String query) {
        assertThat(ReaderService.isCardNumber(query)).isFalse();
    }
}
