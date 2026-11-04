package io.github.riseoftech.library.readers.category;

import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Reader categories as configured under {@code library.readers.categories}, keyed by lower-case code.
 * Other code uses {@link ReaderCategories}, not this class.
 */
@Validated
@ConfigurationProperties("library.readers")
public record ReaderCategoriesProperties(@NotEmpty Map<String, @Valid Category> categories) {

    /**
     * @param maxAgeExclusive the reader must be younger than this (full years) on the current date;
     *                        {@code null} means no age check
     */
    public record Category(@Positive int loanLimit, @Positive int loanPeriodDays, @Positive Integer maxAgeExclusive) {
    }
}
