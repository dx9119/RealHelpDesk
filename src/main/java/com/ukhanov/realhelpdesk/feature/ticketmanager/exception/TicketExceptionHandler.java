package com.ukhanov.realhelpdesk.feature.ticketmanager.exception;

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
public class TicketExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(TicketExceptionHandler.class);

    @ExceptionHandler(TicketException.class)
    public ResponseEntity<Map<String, String>> handleTicketException(TicketException ex, WebRequest request) {
        HttpStatus status = HttpStatus.CONFLICT;

        Map<String, String> error = new HashMap<>();
        error.put("Сообщение", ex.getMessage());
        error.put("Путь", request.getDescription(false));
        error.put("Подробнее", ex.getCause() != null ? ex.getCause().getMessage() : "none");

        logger.error(ex.getMessage());

        return new ResponseEntity<>(error, status);
    }
}
