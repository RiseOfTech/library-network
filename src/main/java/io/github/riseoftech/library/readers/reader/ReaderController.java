package io.github.riseoftech.library.readers.reader;

import java.net.URI;

import io.github.riseoftech.library.common.PageResponse;
import io.github.riseoftech.library.readers.category.KnownCategory;
import io.github.riseoftech.library.readers.reader.ReaderDtos.ReaderRequest;
import io.github.riseoftech.library.readers.reader.ReaderDtos.ReaderResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/readers")
public class ReaderController {

    private final ReaderService service;

    public ReaderController(ReaderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ReaderResponse> register(@Valid @RequestBody ReaderRequest request) {
        ReaderResponse reader = service.register(request);
        return ResponseEntity.created(URI.create("/api/readers/" + reader.id())).body(reader);
    }

    @PutMapping("/{id}")
    public ReaderResponse update(@PathVariable long id, @Valid @RequestBody ReaderRequest request) {
        return service.update(id, request);
    }

    @GetMapping("/{id}")
    public ReaderResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @GetMapping
    public PageResponse<ReaderResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @KnownCategory String category,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.search(q, category, page, size);
    }
}
