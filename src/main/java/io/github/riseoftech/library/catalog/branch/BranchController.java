package io.github.riseoftech.library.catalog.branch;

import java.net.URI;
import java.util.List;

import io.github.riseoftech.library.catalog.branch.BranchDtos.BranchResponse;
import io.github.riseoftech.library.catalog.branch.BranchDtos.CreateBranchRequest;
import io.github.riseoftech.library.catalog.branch.BranchDtos.UpdateBranchRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/branches")
public class BranchController {

    private final BranchService service;

    public BranchController(BranchService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<BranchResponse> create(@Valid @RequestBody CreateBranchRequest request) {
        BranchResponse branch = service.create(request);
        return ResponseEntity.created(URI.create("/api/branches/" + branch.id())).body(branch);
    }

    @PutMapping("/{id}")
    public BranchResponse update(@PathVariable long id, @Valid @RequestBody UpdateBranchRequest request) {
        return service.update(id, request);
    }

    @GetMapping("/{id}")
    public BranchResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @GetMapping
    public List<BranchResponse> list() {
        return service.list();
    }
}
