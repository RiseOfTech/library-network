package io.github.riseoftech.library.readers.category;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.riseoftech.library.IntegrationTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

class ReaderCategoryApiTest extends IntegrationTest {

    @Test
    void defaultCategoriesAreListed() {
        assertThat(mvc.get().uri("/api/reader-categories"))
                .hasStatus(HttpStatus.OK)
                .bodyJson().isStrictlyEqualTo("""
                        [
                          {"code": "ADULT", "loanLimit": 5, "loanPeriodDays": 21, "maxAgeExclusive": null},
                          {"code": "CHILD", "loanLimit": 3, "loanPeriodDays": 14, "maxAgeExclusive": 14},
                          {"code": "STUDENT", "loanLimit": 10, "loanPeriodDays": 30, "maxAgeExclusive": null}
                        ]""");
    }

    @Nested
    @TestPropertySource(properties = "library.readers.categories.student.loan-limit=8")
    class WithOverriddenStudentLimit {

        @Autowired
        MockMvcTester mvc;

        @Test
        void configuredNumbersAreUsed() {
            assertThat(mvc.get().uri("/api/reader-categories"))
                    .hasStatus(HttpStatus.OK)
                    .bodyJson().extractingPath("$[?(@.code == 'STUDENT')].loanLimit").asArray()
                    .containsExactly(8);
        }
    }
}
