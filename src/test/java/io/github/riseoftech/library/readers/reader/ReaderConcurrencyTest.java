package io.github.riseoftech.library.readers.reader;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.jayway.jsonpath.JsonPath;
import io.github.riseoftech.library.IntegrationTest;
import io.github.riseoftech.library.common.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class ReaderConcurrencyTest extends IntegrationTest {

    private static final int PARALLEL_REGISTRATIONS = 8;
    private static final int ROUNDS = 10;

    @Autowired
    ReaderRepository readers;

    @BeforeEach
    void cleanDatabase() {
        truncate("loan", "reader");
    }

    @Test
    void parallelRegistrationsGetDistinctCardNumbers() throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(PARALLEL_REGISTRATIONS)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<MvcResult>> results = new ArrayList<>();
            for (int i = 0; i < PARALLEL_REGISTRATIONS; i++) {
                String body = request("Reader", "Number " + i, "reader" + i + "@example.org");
                results.add(executor.submit(() -> {
                    start.await();
                    return mvc.post().uri("/api/readers").contentType(MediaType.APPLICATION_JSON).content(body)
                            .exchange().getMvcResult();
                }));
            }
            start.countDown();

            Set<String> cardNumbers = new HashSet<>();
            for (Future<MvcResult> result : results) {
                MvcResult mvcResult = result.get();
                assertThat(mvcResult.getResponse().getStatus()).isEqualTo(201);
                cardNumbers.add(JsonPath.read(mvcResult.getResponse().getContentAsString(), "$.cardNumber"));
            }
            assertThat(cardNumbers).hasSize(PARALLEL_REGISTRATIONS);
        }
        assertThat(readers.count()).isEqualTo(PARALLEL_REGISTRATIONS);
    }

    @Test
    void parallelUpdatesNeverMixTwoRequests() throws Exception {
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            for (int round = 0; round < ROUNDS; round++) {
                Reader reader = readers.save(new Reader("LN-%06d".formatted(readers.nextCardNumber()),
                        "Anna", "Petrova", LocalDate.of(1990, 5, 17), "ADULT", "anna@example.org", null));
                List<String> bodies = List.of(
                        request("Anna", "Ivanova", "ivanova@example.org"),
                        request("Anna", "Sidorova", "sidorova@example.org"));

                CountDownLatch start = new CountDownLatch(1);
                List<Future<MvcResult>> results = new ArrayList<>();
                for (String body : bodies) {
                    results.add(executor.submit(() -> {
                        start.await();
                        return mvc.put().uri("/api/readers/{id}", reader.getId())
                                .contentType(MediaType.APPLICATION_JSON).content(body)
                                .exchange().getMvcResult();
                    }));
                }
                start.countDown();

                List<Integer> statuses = new ArrayList<>();
                for (Future<MvcResult> result : results) {
                    MvcResult mvcResult = result.get();
                    int status = mvcResult.getResponse().getStatus();
                    statuses.add(status);
                    if (status == 409) {
                        assertThat(mvcResult.getResponse().getContentAsString())
                                .contains(ErrorCodes.CONCURRENT_MODIFICATION);
                    }
                }
                assertThat(statuses).as("round %d", round).contains(200).allMatch(s -> s == 200 || s == 409);

                Reader stored = readers.findById(reader.getId()).orElseThrow();
                assertThat(List.of(stored.getLastName(), stored.getEmail())).as("round %d", round)
                        .isIn(List.of("Ivanova", "ivanova@example.org"), List.of("Sidorova", "sidorova@example.org"));
            }
        }
    }

    private static String request(String firstName, String lastName, String email) {
        return """
                {"firstName": "%s", "lastName": "%s", "dateOfBirth": "1990-05-17",
                 "category": "ADULT", "email": "%s"}""".formatted(firstName, lastName, email);
    }
}
