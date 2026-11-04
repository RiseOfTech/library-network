package io.github.riseoftech.library.catalog.book;

import java.net.URI;

import io.github.riseoftech.library.catalog.book.BookDtos.BookRequest;
import io.github.riseoftech.library.catalog.book.BookDtos.BookResponse;
import io.github.riseoftech.library.common.PageResponse;
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
@RequestMapping("/api/books")
public class BookController {

    private final BookService service;

    public BookController(BookService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<BookResponse> create(@Valid @RequestBody BookRequest request) {
        BookResponse book = service.create(request);
        return ResponseEntity.created(URI.create("/api/books/" + book.id())).body(book);
    }

    @PutMapping("/{id}")
    public BookResponse update(@PathVariable long id, @Valid @RequestBody BookRequest request) {
        return service.update(id, request);
    }

    @GetMapping("/{id}")
    public BookResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @GetMapping
    public PageResponse<BookResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.search(q, page, size);
    }
}
