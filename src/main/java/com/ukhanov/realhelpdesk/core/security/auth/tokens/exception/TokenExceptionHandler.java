package com.ukhanov.realhelpdesk.core.security.auth.tokens.exception;

import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
@Order(1)
public class TokenExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(TokenExceptionHandler.class);

    @ExceptionHandler({TokenException.class, JwtException.class})
    public ResponseEntity<Map<String, String>> handleTokenException(Exception ex, WebRequest request) {
        HttpStatus status = HttpStatus.CONFLICT;

        Map<String, String> error = new HashMap<>();
        error.put("message", ex.getMessage());
        error.put("path", request.getDescription(false));
        error.put("cause", ex.getCause() != null ? ex.getCause().getMessage() : "none");

        logger.debug("Logout handler caught: {}", ex.getClass().getName());

        return new ResponseEntity<>(error, status);
    }
}
