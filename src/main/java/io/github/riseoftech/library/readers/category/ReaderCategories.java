package io.github.riseoftech.library.readers.category;

import java.time.LocalDate;
import java.time.Period;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

/**
 * The configured reader categories, looked up by their upper-case code.
 */
@Component
public class ReaderCategories {

    private final SortedMap<String, ReaderCategory> byCode;

    public ReaderCategories(ReaderCategoriesProperties properties) {
        SortedMap<String, ReaderCategory> categories = new TreeMap<>();
        properties.categories().forEach((key, value) -> {
            String code = key.toUpperCase(Locale.ROOT);
            ReaderCategory category = new ReaderCategory(
                    code, value.loanLimit(), value.loanPeriodDays(), value.maxAgeExclusive());
            if (categories.putIfAbsent(code, category) != null) {
                throw new IllegalStateException("Reader category " + code + " is configured more than once");
            }
        });
        this.byCode = Collections.unmodifiableSortedMap(categories);
    }

    /** Exact, case-sensitive match on the upper-case code. */
    public Optional<ReaderCategory> find(String code) {
        return Optional.ofNullable(code).map(byCode::get);
    }

    /** All categories ordered by code. */
    public List<ReaderCategory> all() {
        return List.copyOf(byCode.values());
    }

    /** True if a reader born on {@code dateOfBirth} may have {@code category} on {@code today}. */
    public boolean isAllowedFor(ReaderCategory category, LocalDate dateOfBirth, LocalDate today) {
        if (category.maxAgeExclusive() == null) {
            return true;
        }
        return Period.between(dateOfBirth, today).getYears() < category.maxAgeExclusive();
    }
}
