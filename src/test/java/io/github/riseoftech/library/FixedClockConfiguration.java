package io.github.riseoftech.library;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the application {@link Clock} with one fixed at {@link #TODAY}, so rules that depend
 * on the current date give the same result on every run.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FixedClockConfiguration {

    public static final LocalDate TODAY = LocalDate.of(2026, 11, 4);

    @Bean
    @Primary
    Clock fixedClock() {
        return Clock.fixed(TODAY.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    }
}
