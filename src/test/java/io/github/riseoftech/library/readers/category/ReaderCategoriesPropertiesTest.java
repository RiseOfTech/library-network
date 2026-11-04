package io.github.riseoftech.library.readers.category;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A broken {@code library.readers} configuration stops the application at startup.
 */
class ReaderCategoriesPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class);

    @Test
    void validConfigurationStarts() {
        runner.withPropertyValues(
                        "library.readers.categories.adult.loan-limit=5",
                        "library.readers.categories.adult.loan-period-days=21")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ReaderCategories.class).all())
                            .containsExactly(new ReaderCategory("ADULT", 5, 21, null));
                });
    }

    @Test
    void noCategoriesFailsStartup() {
        runner.run(context -> assertThat(context).hasFailed()
                .getFailure().rootCause().hasMessageContaining("categories"));
    }

    @Test
    void zeroLoanLimitFailsStartup() {
        runner.withPropertyValues(
                        "library.readers.categories.adult.loan-limit=0",
                        "library.readers.categories.adult.loan-period-days=21")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("loanLimit"));
    }

    @Test
    void zeroMaxAgeFailsStartup() {
        runner.withPropertyValues(
                        "library.readers.categories.child.loan-limit=3",
                        "library.readers.categories.child.loan-period-days=14",
                        "library.readers.categories.child.max-age-exclusive=0")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("maxAgeExclusive"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ReaderCategoriesProperties.class)
    static class Config {

        @Bean
        ReaderCategories readerCategories(ReaderCategoriesProperties properties) {
            return new ReaderCategories(properties);
        }
    }
}
