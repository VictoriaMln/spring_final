package com.example.hotel_service.api;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleRse(ResponseStatusException ex, HttpServletRequest req) {
        HttpStatusCode code = ex.getStatusCode();

        ErrorResponse body = new ErrorResponse();
        body.setStatus(code.value());
        body.setError(HttpStatus.valueOf(code.value()).getReasonPhrase());
        body.setMessage(ex.getReason());
        body.setPath(req.getRequestURI());
        body.setCorrelationId(MDC.get("correlationId"));

        return ResponseEntity.status(code).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleOther(Exception ex, HttpServletRequest req) {
        ErrorResponse body = new ErrorResponse();
        body.setStatus(500);
        body.setError("Internal Server Error");
        body.setMessage(ex.getMessage());
        body.setPath(req.getRequestURI());
        body.setCorrelationId(MDC.get("correlationId"));

        return ResponseEntity.status(500).body(body);
    }
}
