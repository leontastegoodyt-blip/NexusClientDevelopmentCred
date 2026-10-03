package com.nexusclient.backend;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/** ErrorReplies – every error the API sends back looks the same: {"error": "a plain-English reason"}. */
@RestControllerAdvice
public class ErrorReplies {

    @ExceptionHandler(NexusException.class)
    public ResponseEntity<Map<String, String>> nexus(NexusException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException e) {
        String field = e.getBindingResult().getFieldError() == null ? "request"
                : e.getBindingResult().getFieldError().getField();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid " + field));
    }
}
