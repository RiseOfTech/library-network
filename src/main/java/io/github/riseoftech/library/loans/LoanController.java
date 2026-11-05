package io.github.riseoftech.library.loans;

import java.net.URI;

import io.github.riseoftech.library.common.PageResponse;
import io.github.riseoftech.library.loans.LoanDtos.CheckoutRequest;
import io.github.riseoftech.library.loans.LoanDtos.LoanResponse;
import io.github.riseoftech.library.loans.LoanDtos.ReturnRequest;
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
@RequestMapping("/api/loans")
public class LoanController {

    private final LoanService service;

    public LoanController(LoanService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<LoanResponse> checkout(@Valid @RequestBody CheckoutRequest request) {
        LoanResponse loan = service.checkout(request);
        return ResponseEntity.created(URI.create("/api/loans/" + loan.id())).body(loan);
    }

    @PostMapping("/{id}/return")
    public LoanResponse returnLoan(@PathVariable long id, @Valid @RequestBody ReturnRequest request) {
        return service.returnLoan(id, request);
    }

    @GetMapping("/{id}")
    public LoanResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @GetMapping
    public PageResponse<LoanResponse> list(
            @RequestParam(required = false) Long readerId,
            @RequestParam(required = false) Long copyId,
            @RequestParam(required = false) LoanStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(readerId, copyId, status, page, size);
    }
}
