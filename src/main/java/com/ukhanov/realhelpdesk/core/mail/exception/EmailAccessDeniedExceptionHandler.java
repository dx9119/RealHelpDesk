package com.ukhanov.realhelpdesk.core.mail.exception;

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

@ControllerAdvice
@Order(1)
public class EmailAccessDeniedExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(EmailAccessDeniedExceptionHandler.class);

    @ExceptionHandler(EmailAccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleEmailAccessDenied(
        EmailAccessDeniedException ex, WebRequest request) {
        HttpStatus status = HttpStatus.CONFLICT;

        Map<String, String> error = new HashMap<>();
        error.put("Сообщение", ex.getMessage());
        error.put("Путь", request.getDescription(false));

        logger.error(ex.getMessage());

        return new ResponseEntity<>(error, status);
    }

}
