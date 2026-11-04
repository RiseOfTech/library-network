package io.github.riseoftech.library;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Base class for integration tests: full application context
 * backed by PostgreSQL in Testcontainers and a clock fixed at {@link FixedClockConfiguration#TODAY}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
public abstract class IntegrationTest {

    @Autowired
    protected MockMvcTester mvc;

    @Autowired
    protected JdbcTemplate jdbc;

    /** Empties the given tables so each test starts from a known state. */
    protected void truncate(String... tables) {
        jdbc.execute("truncate table " + String.join(", ", tables) + " restart identity cascade");
    }
}
