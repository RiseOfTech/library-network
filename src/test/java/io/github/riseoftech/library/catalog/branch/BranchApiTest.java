package io.github.riseoftech.library.catalog.branch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.riseoftech.library.IntegrationTest;
import io.github.riseoftech.library.common.ErrorCodes;
import io.github.riseoftech.library.common.UniqueConstraints;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;

class BranchApiTest extends IntegrationTest {

    @Autowired
    BranchRepository repository;

    @BeforeEach
    void cleanDatabase() {
        truncate("loan", "copy", "branch");
    }

    @Test
    void branchIsCreated() {
        var result = assertThat(post("""
                {"code": "CENTRAL", "name": "Central Library", "address": "1 Main St"}"""))
                .hasStatus(HttpStatus.CREATED);
        long id = repository.findAll().getFirst().getId();
        result.headers().hasValue("Location", "/api/branches/" + id);
        var body = result.bodyJson();
        body.extractingPath("$.id").isEqualTo((int) id);
        body.extractingPath("$.code").isEqualTo("CENTRAL");
        body.extractingPath("$.name").isEqualTo("Central Library");
        body.extractingPath("$.address").isEqualTo("1 Main St");
    }

    @Test
    void branchCodeIsAlreadyTaken() {
        createBranch("CENTRAL");

        assertThat(post("""
                {"code": "CENTRAL", "name": "Another", "address": "5 Side St"}"""))
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.code").isEqualTo("BRANCH_CODE_TAKEN");
    }

    @Test
    void blankNameIsRejected() {
        var body = assertThat(post("""
                {"code": "CENTRAL", "name": " ", "address": "1 Main St"}"""))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson();
        body.extractingPath("$.code").isEqualTo(ErrorCodes.VALIDATION_FAILED);
        body.extractingPath("$.errors[*].field").asArray().containsExactly("name");
    }

    @Test
    void malformedCodeIsRejected() {
        var body = assertThat(post("""
                {"code": "central lib", "name": "Central Library", "address": "1 Main St"}"""))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson();
        body.extractingPath("$.code").isEqualTo(ErrorCodes.VALIDATION_FAILED);
        body.extractingPath("$.errors[*].field").asArray().containsExactly("code");
    }

    @Test
    void branchIsUpdated() {
        long id = createBranch("CENTRAL");

        var body = assertThat(mvc.put().uri("/api/branches/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Central City Library", "address": "2 Main St"}"""))
                .hasStatus(HttpStatus.OK)
                .bodyJson();
        body.extractingPath("$.code").isEqualTo("CENTRAL");
        body.extractingPath("$.name").isEqualTo("Central City Library");
        body.extractingPath("$.address").isEqualTo("2 Main St");
        assertThat(repository.findById(id)).get()
                .extracting(Branch::getName).isEqualTo("Central City Library");
    }

    @Test
    void codeInUpdateIsIgnored() {
        long id = createBranch("CENTRAL");

        assertThat(mvc.put().uri("/api/branches/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "OTHER", "name": "Central City Library", "address": "2 Main St"}"""))
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$.code").isEqualTo("CENTRAL");
    }

    @Test
    void updatingUnknownBranch() {
        assertThat(mvc.put().uri("/api/branches/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Central City Library", "address": "2 Main St"}"""))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("BRANCH_NOT_FOUND");
    }

    @Test
    void getBranchById() {
        long id = createBranch("CENTRAL");

        var body = assertThat(mvc.get().uri("/api/branches/{id}", id))
                .hasStatus(HttpStatus.OK)
                .bodyJson();
        body.extractingPath("$.id").isEqualTo((int) id);
        body.extractingPath("$.code").isEqualTo("CENTRAL");
    }

    @Test
    void getUnknownBranch() {
        assertThat(mvc.get().uri("/api/branches/999"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.code").isEqualTo("BRANCH_NOT_FOUND");
    }

    @Test
    void listBranchesOrderedByCode() {
        createBranch("NORTH");
        createBranch("CENTRAL");

        assertThat(mvc.get().uri("/api/branches"))
                .hasStatus(HttpStatus.OK)
                .bodyJson().extractingPath("$[*].code").asArray().containsExactly("CENTRAL", "NORTH");
    }

    @Test
    void duplicateCodeViolatesNamedConstraint() {
        repository.saveAndFlush(new Branch("CENTRAL", "Central", "1 Main St"));

        var ex = catchThrowableOfType(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(new Branch("CENTRAL", "Copy", "2 Main St")));

        assertThat(UniqueConstraints.isViolated(ex, BranchService.CODE_CONSTRAINT)).isTrue();
    }

    private MockMvcRequestBuilder post(String json) {
        return mvc.post().uri("/api/branches").contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private long createBranch(String code) {
        return repository.save(new Branch(code, code + " Library", "1 Main St")).getId();
    }
}
