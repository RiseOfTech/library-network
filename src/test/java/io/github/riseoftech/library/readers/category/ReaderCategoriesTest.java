package io.github.riseoftech.library.readers.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.riseoftech.library.readers.category.ReaderCategoriesProperties.Category;
import org.junit.jupiter.api.Test;

class ReaderCategoriesTest {

    private static final LocalDate FOURTEENTH_BIRTHDAY = LocalDate.of(2026, 11, 4);
    private static final LocalDate BORN = LocalDate.of(2012, 11, 4);

    private final ReaderCategories categories = defaults();

    @Test
    void codesAreUpperCasedAndOrdered() {
        assertThat(categories.all()).extracting(ReaderCategory::code)
                .containsExactly("ADULT", "CHILD", "STUDENT");
        assertThat(categories.find("CHILD")).contains(new ReaderCategory("CHILD", 3, 14, 14));
    }

    @Test
    void lookupIsExact() {
        assertThat(categories.find("child")).isEmpty();
        assertThat(categories.find("SENIOR")).isEmpty();
        assertThat(categories.find(null)).isEmpty();
    }

    @Test
    void sameCodeTwiceIsRejected() {
        Map<String, Category> map = new LinkedHashMap<>();
        map.put("adult", new Category(5, 21, null));
        map.put("ADULT", new Category(6, 21, null));

        assertThatIllegalStateException()
                .isThrownBy(() -> new ReaderCategories(new ReaderCategoriesProperties(map)))
                .withMessageContaining("ADULT");
    }

    @Test
    void ageLimitAroundFourteenthBirthday() {
        ReaderCategory child = categories.find("CHILD").orElseThrow();

        assertThat(categories.isAllowedFor(child, BORN, FOURTEENTH_BIRTHDAY.minusDays(1))).isTrue();
        assertThat(categories.isAllowedFor(child, BORN, FOURTEENTH_BIRTHDAY)).isFalse();
        assertThat(categories.isAllowedFor(child, BORN, FOURTEENTH_BIRTHDAY.plusDays(1))).isFalse();
    }

    @Test
    void categoryWithoutAgeLimitAllowsAnyAge() {
        ReaderCategory adult = categories.find("ADULT").orElseThrow();

        assertThat(categories.isAllowedFor(adult, FOURTEENTH_BIRTHDAY, FOURTEENTH_BIRTHDAY)).isTrue();
        assertThat(categories.isAllowedFor(adult, LocalDate.of(1930, 1, 1), FOURTEENTH_BIRTHDAY)).isTrue();
    }

    static ReaderCategories defaults() {
        return new ReaderCategories(new ReaderCategoriesProperties(Map.of(
                "student", new Category(10, 30, null),
                "adult", new Category(5, 21, null),
                "child", new Category(3, 14, 14))));
    }
}
