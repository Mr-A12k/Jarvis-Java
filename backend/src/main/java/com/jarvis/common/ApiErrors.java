package com.jarvis.common;

import java.util.concurrent.TimeoutException;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@RestControllerAdvice
public class ApiErrors {
    public record ErrorBody(String code, String message) {}
    public static ApiException modelError(Throwable error) {
        if (error instanceof ApiException known) return known;
        if (error instanceof TimeoutException) return new ApiException(HttpStatus.GATEWAY_TIMEOUT, "MODEL_TIMEOUT", "The local model exceeded its time limit. Try a shorter request.");
        if (error instanceof WebClientResponseException r && r.getStatusCode().value() == 404)
            return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MODEL_MISSING", "The configured model is not installed. Pull it with Ollama and retry.");
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MODEL_UNAVAILABLE", "The local model could not complete this request. Check Ollama and available memory.");
    }
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorBody> known(ApiException e) { return ResponseEntity.status(e.status()).body(new ErrorBody(e.code(), e.getMessage())); }
    @ExceptionHandler({WebExchangeBindException.class, ServerWebInputException.class})
    ResponseEntity<ErrorBody> invalid(Exception e) { return ResponseEntity.badRequest().body(new ErrorBody("INVALID_REQUEST", "Check required fields, value types and size limits for this request.")); }
    @ExceptionHandler(DataBufferLimitException.class)
    ResponseEntity<ErrorBody> tooLarge(Exception e) { return ResponseEntity.status(413).body(new ErrorBody("REQUEST_TOO_LARGE", "Request body exceeds size limits.")); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> unexpected(Exception e) { return ResponseEntity.internalServerError().body(new ErrorBody("INTERNAL_ERROR", "The request failed. Retry or check the local service.")); }
}
