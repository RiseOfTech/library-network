package io.github.riseoftech.library.catalog.copy;

import java.net.URI;
import java.util.List;

import io.github.riseoftech.library.catalog.copy.CopyDtos.CopyResponse;
import io.github.riseoftech.library.catalog.copy.CopyDtos.RegisterCopyRequest;
import io.github.riseoftech.library.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CopyController {

    private final CopyService service;

    public CopyController(CopyService service) {
        this.service = service;
    }

    @PostMapping("/copies")
    public ResponseEntity<CopyResponse> register(@Valid @RequestBody RegisterCopyRequest request) {
        CopyResponse copy = service.register(request);
        return ResponseEntity.created(URI.create("/api/copies/" + copy.id())).body(copy);
    }

    @GetMapping("/copies/{id}")
    public CopyResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @GetMapping("/copies")
    public PageResponse<CopyResponse> list(
            @RequestParam(required = false) Long bookId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) CopyStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(bookId, branchId, status, page, size);
    }

    @PostMapping("/copies/{id}/lost")
    public CopyResponse markLost(@PathVariable long id) {
        return service.markLost(id);
    }

    @PostMapping("/copies/{id}/found")
    public CopyResponse markFound(@PathVariable long id) {
        return service.markFound(id);
    }

    @GetMapping("/books/{id}/availability")
    public List<BranchAvailability> availability(@PathVariable long id) {
        return service.availability(id);
    }
}
