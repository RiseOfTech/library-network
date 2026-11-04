package io.github.riseoftech.library.readers.reader;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.riseoftech.library.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ReaderRepositoryTest extends IntegrationTest {

    @Autowired
    ReaderRepository readers;

    @Test
    void cardNumbersFromSequenceIncrease() {
        long first = readers.nextCardNumber();
        long second = readers.nextCardNumber();
        long third = readers.nextCardNumber();

        assertThat(first).isPositive();
        assertThat(second).isGreaterThan(first);
        assertThat(third).isGreaterThan(second);
    }
}
