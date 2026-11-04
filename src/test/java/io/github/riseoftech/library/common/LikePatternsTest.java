package io.github.riseoftech.library.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LikePatternsTest {

    @Test
    void wrapsLowerCasedTextInWildcards() {
        assertThat(LikePatterns.contains("Tolstoy")).isEqualTo("%tolstoy%");
    }

    @Test
    void escapesPercent() {
        assertThat(LikePatterns.contains("100%")).isEqualTo("%100\\%%");
    }

    @Test
    void escapesUnderscore() {
        assertThat(LikePatterns.contains("a_b")).isEqualTo("%a\\_b%");
    }

    @Test
    void escapesBackslashBeforeOtherCharacters() {
        assertThat(LikePatterns.contains("a\\%")).isEqualTo("%a\\\\\\%%");
    }

    @Test
    void lowerCasesMixedCaseAndKeepsEscapes() {
        assertThat(LikePatterns.contains("War_And\\PEACE%")).isEqualTo("%war\\_and\\\\peace\\%%");
    }
}
