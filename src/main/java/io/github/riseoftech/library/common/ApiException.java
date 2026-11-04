package io.github.riseoftech.library.common;

import org.springframework.http.HttpStatus;

/**
 * Base class for business errors exposed through the API.
 * Each subclass maps to an HTTP status and a stable machine-readable {@code code}.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
