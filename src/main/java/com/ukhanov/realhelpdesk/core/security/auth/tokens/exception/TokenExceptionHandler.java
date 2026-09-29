package com.ukhanov.realhelpdesk.core.security.auth.tokens.exception;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import io.jsonwebtoken.JwtException;

@ControllerAdvice
@Order(1)
public class TokenExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(TokenExceptionHandler.class);

    @ExceptionHandler({TokenException.class, JwtException.class})
    public ResponseEntity<Map<String, String>> handleTokenException(Exception ex, WebRequest request) {
        HttpStatus status = HttpStatus.CONFLICT;

        Map<String, String> error = new HashMap<>();
        // Внутренние детали (cause, тексты ошибок jjwt) клиенту не отдаём
        error.put("Сообщение", ex instanceof JwtException ? "Недействительный токен" : ex.getMessage());
        error.put("Путь", request.getDescription(false));

        logger.error("{}: {}", ex.getClass().getName(), ex.getMessage());

        return new ResponseEntity<>(error, status);
    }
}
