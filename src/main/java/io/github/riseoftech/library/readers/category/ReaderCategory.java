package io.github.riseoftech.library.readers.category;

/**
 * A reader category: how many loans a reader may have at the same time and for how many days.
 *
 * @param code            upper-case code, the value of a reader's {@code category}
 * @param maxAgeExclusive the reader must be younger than this (full years); {@code null} means no age check
 */
public record ReaderCategory(String code, int loanLimit, int loanPeriodDays, Integer maxAgeExclusive) {
}
