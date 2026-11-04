package io.github.riseoftech.library;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

class ApplicationSmokeTest extends IntegrationTest {

    @Autowired
    Flyway flyway;

    @Test
    void migrationsAreApplied() {
        assertThat(flyway.info().applied()).isNotEmpty();
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void healthIsUp() {
        assertThat(mvc.get().uri("/actuator/health"))
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.status").isEqualTo("UP");
    }

    @Test
    void openApiDocsAreServed() {
        assertThat(mvc.get().uri("/v3/api-docs"))
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.openapi").asString().startsWith("3.");
    }

    @Test
    void loanEndpointsAreDocumented() {
        var body = assertThat(mvc.get().uri("/v3/api-docs"))
                .hasStatus(HttpStatus.OK)
                .bodyJson();
        body.extractingPath("$.paths['/api/loans']").asMap().containsKeys("get", "post");
        body.extractingPath("$.paths['/api/loans/{id}']").asMap().containsKey("get");
        body.extractingPath("$.paths['/api/loans/{id}/return']").asMap().containsKey("post");
    }
}
