package io.github.riseoftech.library;

import org.springframework.boot.SpringApplication;

/**
 * Runs the application against a throwaway Testcontainers PostgreSQL,
 * without docker compose.
 */
public class TestLibraryNetworkApplication {

    public static void main(String[] args) {
        SpringApplication.from(LibraryNetworkApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
