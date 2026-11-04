package io.github.riseoftech.library.readers.reader;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import com.jayway.jsonpath.JsonPath;
import io.github.riseoftech.library.IntegrationTest;
import io.github.riseoftech.library.common.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;

/**
 * Reader API scenarios; today is fixed at 2026-11-04.
 */
class ReaderApiTest extends IntegrationTest {

    private static final String ANNA = """
            {"firstName": "Anna", "lastName": "Petrova", "dateOfBirth": "1990-05-17",
             "category": "ADULT", "email": "anna@example.org"}""";

    @Autowired
    ReaderRepository repository;

    @BeforeEach
    void cleanDatabase() {
        truncate("reader");
    }

    // --- registration ---

    @Test
    void readerIsRegistered() {
        var result = assertThat(post(ANNA)).hasStatus(HttpStatus.CREATED);
        Reader saved = repository.findAll().getFirst();
        result.headers().hasValue("Location", "/api/readers/" + saved.getId());
        var body = result.bodyJson();
        body.extractingPath("$.id").isEqualTo(saved.getId().intValue());
        body.extractingPath("$.cardNumber").isEqualTo(saved.getCardNumber());
        body.extractingPath("$.firstName").isEqualTo("Anna");
        body.extractingPath("$.lastName").isEqualTo("Petrova");
        body.extractingPath("$.dateOfBirth").isEqualTo("1990-05-17");
        body.extractingPath("$.category").isEqualTo("ADULT");
        body.extractingPath("$.email").isEqualTo("anna@example.org");
        body.extractingPath("$.phone").isNull();
    }

    @Test
    void readerWithPhoneOnly() {
        var body = assertThat(post(reader("ADULT", "1990-05-17", null, "+7 900 123-45-67")))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson();
        body.extractingPath("$.phone").isEqualTo("+7 900 123-45-67");
        body.extractingPath("$.email").isNull();
    }

    @Test
    void blankEmailIsStoredAsNull() {
        assertThat(post(reader("ADULT", "1990-05-17", "", "+7 900 123-45-67")))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.email").isNull();
        assertThat(repository.findAll().getFirst().getEmail()).isNull();
    }

    @Test
    void missingRequiredFields() {
        assertValidationFailed(post("""
                {"firstName": " ", "email": "anna@example.org"}"""),
                "firstName", "lastName", "dateOfBirth", "category");
    }

    @Test
    void noContactGiven() {
        assertValidationFailed(post(reader("ADULT", "1990-05-17", null, null)), "email");
    }

    @Test
    void blankContactsCountAsMissing() {
        assertValidationFailed(post(reader("ADULT", "1990-05-17", "", "")), "email");
    }

    @Test
    void invalidEmail() {
        assertValidationFailed(post(reader("ADULT", "1990-05-17", "not-an-email", null)), "email");
    }

    @Test
    void invalidPhone() {
        assertValidationFailed(post(reader("ADULT", "1990-05-17", null, "call me")), "phone");
    }

    @Test
    void phoneLongerThanColumnIsRejected() {
        assertValidationFailed(post(reader("ADULT", "1990-05-17", null, "+12345678901234567890")), "phone");
    }

    @Test
    void dateOfBirthInTheFuture() {
        assertValidationFailed(post(reader("ADULT", "2026-11-05", "anna@example.org", null)), "dateOfBirth");
    }

    @Test
    void sameEmailForTwoReaders() {
        assertThat(post(reader("ADULT", "1980-01-01", "family@example.org", null))).hasStatus(HttpStatus.CREATED);
        assertThat(post(reader("CHILD", "2018-01-01", "family@example.org", null))).hasStatus(HttpStatus.CREATED);
    }

    // --- card number ---

    @Test
    void cardNumbersAreSequentialAndUnique() throws Exception {
        String first = cardNumberOf(post(ANNA));
        String second = cardNumberOf(post(ANNA));

        assertThat(first).matches("LN-\\d{6,}");
        assertThat(second).matches("LN-\\d{6,}");
        assertThat(Long.parseLong(second.substring(3))).isGreaterThan(Long.parseLong(first.substring(3)));
    }

    @Test
    void cardNumberInRequestIsIgnoredOnRegistration() {
        assertThat(post("""
                {"firstName": "Anna", "lastName": "Petrova", "dateOfBirth": "1990-05-17",
                 "category": "ADULT", "email": "anna@example.org", "cardNumber": "LN-999999"}"""))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.cardNumber").isNotEqualTo("LN-999999");
    }

    @Test
    void cardNumberInRequestIsIgnoredOnUpdate() {
        Reader reader = save("Anna", "Petrova", "1990-05-17", "ADULT");

        assertThat(put(reader.getId(), """
                {"firstName": "Anna", "lastName": "Petrova", "dateOfBirth": "1990-05-17",
                 "category": "ADULT", "email": "anna@example.org", "cardNumber": "LN-999999"}"""))
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.cardNumber").isEqualTo(reader.getCardNumber());
        assertThat(load(reader).getCardNumber()).isEqualTo(reader.getCardNumber());
    }

    // --- categories ---

    @Test
    void knownCategoryIsAccepted() {
        assertThat(post(reader("STUDENT", "2004-09-01", "s@example.org", null)))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.category").isEqualTo("STUDENT");
    }

    @Test
    void unknownCategoryIsRejected() {
        assertValidationFailed(post(reader("SENIOR", "1950-01-01", "s@example.org", null)), "category");
    }

    @Test
    void categoryIsMatchedExactly() {
        assertValidationFailed(post(reader("student", "2004-09-01", "s@example.org", null)), "category");
    }

    @Test
    void unknownCategoryInSearchFilter() {
        assertValidationFailed(mvc.get().uri("/api/readers?category=SENIOR"), "category");
    }

    // --- category age limit ---

    @Test
    void childCategoryForThirteenYearOld() {
        assertThat(post(reader("CHILD", "2012-11-05", "kid@example.org", null)))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.category").isEqualTo("CHILD");
    }

    @Test
    void childCategoryOnFourteenthBirthday() {
        assertThat(post(reader("CHILD", "2012-11-04", "kid@example.org", null)))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo(ReaderCategoryNotAllowedException.CODE);
        assertThat(repository.count()).isZero();
    }

    @Test
    void changingAdultToChild() {
        Reader reader = save("Anna", "Petrova", "1990-01-01", "ADULT");

        assertThat(put(reader.getId(), reader("CHILD", "1990-01-01", "anna@example.org", null)))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo(ReaderCategoryNotAllowedException.CODE);
        assertThat(load(reader).getCategory()).isEqualTo("ADULT");
    }

    @Test
    void changingDateOfBirthOfChild() {
        Reader reader = save("Anna", "Petrova", "2016-03-01", "CHILD");

        assertThat(put(reader.getId(), reader("CHILD", "2006-03-01", "anna@example.org", null)))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.code").isEqualTo(ReaderCategoryNotAllowedException.CODE);
        assertThat(load(reader).getDateOfBirth()).isEqualTo(LocalDate.of(2016, 3, 1));
    }

    @Test
    void childWhoHasGrownUpCanStillBeUpdated() {
        Reader reader = save("Anna", "Petrova", "2012-11-04", "CHILD");

        var body = assertThat(put(reader.getId(), reader("CHILD", "2012-11-04", "anna@example.org", "+7 900 000-00-01")))
                .hasStatus(HttpStatus.OK)
                .bodyJson();
        body.extractingPath("$.phone").isEqualTo("+7 900 000-00-01");
        body.extractingPath("$.category").isEqualTo("CHILD");
    }

    @Test
    void adultCategoryHasNoAgeCheck() {
        assertThat(post(reader("ADULT", "2016-01-01", "kid@example.org", null)))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.category").isEqualTo("ADULT");
    }

    // --- update / get ---

    @Test
    void readerIsUpdated() {
        Reader reader = save("Anna", "Petrova", "1990-05-17", "ADULT");

        var body = assertThat(put(reader.getId(), """
                {"firstName": "Anna", "lastName": "Sidorova", "dateOfBirth": "1990-05-17",
                 "category": "STUDENT", "email": "anna.s@example.org"}"""))
                .hasStatus(HttpStatus.OK)
                .bodyJson();
        body.extractingPath("$.lastName").isEqualTo("Sidorova");
        body.extractingPath("$.email").isEqualTo("anna.s@example.org");
        body.extractingPath("$.category").isEqualTo("STUDENT");
        body.extractingPath("$.cardNumber").isEqualTo(reader.getCardNumber());
    }

    @Test
    void updateRemovingEveryContact() {
        Reader reader = save("Anna", "Petrova", "1990-05-17", "ADULT");

        assertValidationFailed(put(reader.getId(), reader("ADULT", "1990-05-17", null, null)), "email");
        assertThat(load(reader).getEmail()).isEqualTo("anna@example.org");
    }

    @Test
    void updatingUnknownReader() {
        assertThat(put(999, ANNA))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo(ReaderNotFoundException.CODE);
    }

    @Test
    void getReaderById() {
        Reader reader = save("Anna", "Petrova", "1990-05-17", "ADULT");

        var body = assertThat(mvc.get().uri("/api/readers/{id}", reader.getId()))
                .hasStatus(HttpStatus.OK)
                .bodyJson();
        body.extractingPath("$.id").isEqualTo(reader.getId().intValue());
        body.extractingPath("$.cardNumber").isEqualTo(reader.getCardNumber());
        body.extractingPath("$.lastName").isEqualTo("Petrova");
    }

    @Test
    void getUnknownReader() {
        assertThat(mvc.get().uri("/api/readers/999"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo(ReaderNotFoundException.CODE);
    }

    // --- search ---

    @Test
    void searchByCardNumber() {
        repository.save(new Reader("LN-000042", "Anna", "Petrova", LocalDate.of(1990, 5, 17), "ADULT",
                "anna@example.org", null));
        save("Petr", "Ivanov", "1985-02-02", "ADULT");

        var body = assertThat(mvc.get().uri("/api/readers?q=ln-000042"))
                .hasStatus(HttpStatus.OK)
                .bodyJson();
        body.extractingPath("$.items[*].cardNumber").asArray().containsExactly("LN-000042");
        body.extractingPath("$.totalElements").isEqualTo(1);
    }

    @Test
    void cardNumberThatDoesNotExist() {
        createSearchFixture();

        var body = assertThat(mvc.get().uri("/api/readers?q=LN-999999"))
                .hasStatus(HttpStatus.OK)
                .bodyJson();
        body.extractingPath("$.items").asArray().isEmpty();
        body.extractingPath("$.totalElements").isEqualTo(0);
    }

    @Test
    void searchByNameFragment() {
        createSearchFixture();

        assertThat(mvc.get().uri("/api/readers?q=petr"))
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.items[*].lastName").asArray()
                .containsExactly("Ivanov", "Petrova");
    }

    @Test
    void searchByFullName() {
        createSearchFixture();

        assertThat(mvc.get().uri("/api/readers").queryParam("q", "anna petr"))
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.items[*].lastName").asArray()
                .containsExactly("Petrova");
    }

    @Test
    void wildcardCharactersAreTakenLiterally() {
        createSearchFixture();

        assertThat(mvc.get().uri("/api/readers").queryParam("q", "_"))
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.totalElements").isEqualTo(0);
    }

    @Test
    void filterByCategory() {
        createSearchFixture();

        assertThat(mvc.get().uri("/api/readers?category=CHILD"))
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.items[*].category").asArray()
                .containsExactly("CHILD");
    }

    @Test
    void nameSearchWithCategory() {
        createSearchFixture();

        assertThat(mvc.get().uri("/api/readers?q=ov&category=ADULT"))
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.items[*].lastName").asArray()
                .containsExactly("Ivanov", "Petrova");
    }

    @Test
    void withoutFiltersAllReadersOrderedByName() {
        createSearchFixture();
        save("Boris", "Ivanov", "1970-07-07", "ADULT");

        var body = assertThat(mvc.get().uri("/api/readers"))
                .hasStatus(HttpStatus.OK)
                .bodyJson();
        body.extractingPath("$.items[*].firstName").asArray().containsExactly("Boris", "Petr", "Anna", "Olga");
        body.extractingPath("$.page").isEqualTo(0);
        body.extractingPath("$.size").isEqualTo(20);
    }

    @Test
    void paginatedReaderList() {
        for (int i = 1; i <= 25; i++) {
            save("Reader", "Name %02d".formatted(i), "1990-01-01", "ADULT");
        }

        var body = assertThat(mvc.get().uri("/api/readers?page=2&size=10"))
                .hasStatus(HttpStatus.OK)
                .bodyJson();
        body.extractingPath("$.items").asArray().hasSize(5);
        body.extractingPath("$.items[0].lastName").isEqualTo("Name 21");
        body.extractingPath("$.page").isEqualTo(2);
        body.extractingPath("$.size").isEqualTo(10);
        body.extractingPath("$.totalElements").isEqualTo(25);
        body.extractingPath("$.totalPages").isEqualTo(3);
    }

    @Test
    void pageSizeOutOfRange() {
        assertValidationFailed(mvc.get().uri("/api/readers?size=0"), "size");
    }

    // --- helpers ---

    private void createSearchFixture() {
        save("Anna", "Petrova", "1990-05-17", "ADULT");
        save("Petr", "Ivanov", "1985-02-02", "ADULT");
        save("Olga", "Sidorova", "2016-06-06", "CHILD");
    }

    private Reader save(String firstName, String lastName, String dateOfBirth, String category) {
        String card = "LN-%06d".formatted(repository.nextCardNumber());
        return repository.save(new Reader(card, firstName, lastName, LocalDate.parse(dateOfBirth), category,
                "anna@example.org", null));
    }

    private Reader load(Reader reader) {
        return repository.findById(reader.getId()).orElseThrow();
    }

    private static String reader(String category, String dateOfBirth, String email, String phone) {
        return """
                {"firstName": "Anna", "lastName": "Petrova", "dateOfBirth": "%s",
                 "category": "%s", "email": %s, "phone": %s}"""
                .formatted(dateOfBirth, category, json(email), json(phone));
    }

    private static String json(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private String cardNumberOf(MockMvcRequestBuilder request) throws Exception {
        var result = request.exchange();
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return JsonPath.read(result.getResponse().getContentAsString(), "$.cardNumber");
    }

    private MockMvcRequestBuilder post(String json) {
        return mvc.post().uri("/api/readers").contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private MockMvcRequestBuilder put(long id, String json) {
        return mvc.put().uri("/api/readers/{id}", id).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private void assertValidationFailed(MockMvcRequestBuilder request, String... fields) {
        var body = assertThat(request)
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson();
        body.extractingPath("$.code").isEqualTo(ErrorCodes.VALIDATION_FAILED);
        body.extractingPath("$.errors[*].field").asArray().containsExactlyInAnyOrder((Object[]) fields);
    }
}
