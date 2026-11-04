package io.github.riseoftech.library.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;

import io.github.riseoftech.library.FixedClockConfiguration;
import io.github.riseoftech.library.IntegrationTest;
import jakarta.validation.Valid;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.PastOrPresent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bean Validation takes "now" from the application {@link java.time.Clock}, fixed in tests.
 */
class ValidationClockTest extends IntegrationTest {

    @Autowired
    private Clock clock;

    @Autowired
    private ValidatorFactory validatorFactory;

    @Test
    void validatorUsesApplicationClock() {
        assertThat(LocalDate.now(clock)).isEqualTo(FixedClockConfiguration.TODAY);
        assertThat(validatorFactory.getClockProvider().getClock()).isSameAs(clock);
    }

    @Test
    void fixedDateIsAccepted() {
        assertThat(post(FixedClockConfiguration.TODAY)).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void dayAfterFixedDateIsRejected() {
        var result = assertThat(post(FixedClockConfiguration.TODAY.plusDays(1)))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson();
        result.extractingPath("$.code").isEqualTo(ErrorCodes.VALIDATION_FAILED);
        result.extractingPath("$.errors[0].field").isEqualTo("date");
    }

    private MockMvcRequestBuilder post(LocalDate date) {
        return mvc.post().uri("/test-clock/past-or-present")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\": \"" + date + "\"}");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Endpoints {

        @Bean
        TestClockController testClockController() {
            return new TestClockController();
        }
    }

    @RestController
    static class TestClockController {

        record Payload(@PastOrPresent LocalDate date) {
        }

        @PostMapping("/test-clock/past-or-present")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        void validate(@Valid @RequestBody Payload payload) {
        }
    }
}
