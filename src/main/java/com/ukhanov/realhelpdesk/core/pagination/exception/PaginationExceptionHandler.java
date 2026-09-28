package com.ukhanov.realhelpdesk.core.pagination.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PaginationExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(PaginationExceptionHandler.class);

    @ExceptionHandler(PaginationException.class)
    public ResponseEntity<Map<String, String>> handlePaginationException(PaginationException ex, WebRequest request) {
        logger.warn("Ошибка пагинации: {}", ex.getMessage());

        Map<String, String> error = new HashMap<>();
        error.put("Сообщение", ex.getMessage());
        error.put("Путь", request.getDescription(false));
        error.put("Подробнее", ex.getCause() != null ? ex.getCause().getMessage() : "none");

        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }
}
