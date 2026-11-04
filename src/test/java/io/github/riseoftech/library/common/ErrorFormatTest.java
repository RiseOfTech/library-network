package io.github.riseoftech.library.common;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.riseoftech.library.IntegrationTest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class ErrorFormatTest extends IntegrationTest {

    @Test
    void unknownPathReturnsProblemWithCode() {
        assertThat(mvc.get().uri("/no-such-path"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
    }

    @Test
    void businessErrorReturnsItsStatusAndCode() {
        var result = assertThat(mvc.get().uri("/test-errors/conflict"))
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson();
        result.extractingPath("$.code").isEqualTo("TEST_CONFLICT");
        result.extractingPath("$.detail").isEqualTo("Something conflicts");
        result.extractingPath("$.status").isEqualTo(409);
    }

    @Test
    void validationErrorListsFields() {
        var result = assertThat(mvc.post().uri("/test-errors/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson();
        result.extractingPath("$.code").isEqualTo(ErrorCodes.VALIDATION_FAILED);
        result.extractingPath("$.errors[0].field").isEqualTo("name");
    }

    @Test
    void malformedBodyReturnsBadRequest() {
        assertThat(mvc.post().uri("/test-errors/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.code").isEqualTo("BAD_REQUEST");
    }

    @Test
    void unexpectedErrorIsHidden() {
        var result = assertThat(mvc.get().uri("/test-errors/boom"))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyJson();
        result.extractingPath("$.code").isEqualTo(ErrorCodes.INTERNAL_ERROR);
        result.extractingPath("$.detail").isEqualTo("Unexpected server error");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Endpoints {

        @Bean
        TestErrorController testErrorController() {
            return new TestErrorController();
        }
    }

    @RestController
    static class TestErrorController {

        record Payload(@NotBlank String name) {
        }

        @GetMapping("/test-errors/conflict")
        void conflict() {
            throw new ConflictException("TEST_CONFLICT", "Something conflicts");
        }

        @PostMapping("/test-errors/validated")
        void validated(@Valid @RequestBody Payload payload) {
        }

        @GetMapping("/test-errors/boom")
        void boom() {
            throw new IllegalStateException("secret internals");
        }
    }
}
