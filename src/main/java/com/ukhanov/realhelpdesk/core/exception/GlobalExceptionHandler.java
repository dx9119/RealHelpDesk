package com.ukhanov.realhelpdesk.core.exception;

import jakarta.mail.MessagingException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> handleConstraintViolationException(ConstraintViolationException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        logger.debug("Global handler caught ConstraintViolationException: {}", ex.getClass().getName());

            Map<String, String> errors = new HashMap<>();
            errors.put("Source:", "Global handler");
            for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
                errors.put(violation.getPropertyPath().toString(), violation.getMessage());
            }

        return new ResponseEntity<>(errors, status);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        logger.debug("Unhandled exception caught: {}", ex.getMessage(), ex);

        Map<String, String> response = Map.of(
                "Source","Global handler",
                "Error", "Operation failed."
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }

    @ExceptionHandler(MailSendException.class)
    public ResponseEntity<Map<String, String>> handleMailSendException(MailSendException ex) {

        Throwable rootCause = ex.getCause();
        String causeMessage = rootCause != null ? rootCause.getMessage() : "Unknown mail cause";
        logger.error("Error when sending an email: {}", causeMessage, ex);

        Map<String, String> response = Map.of(
            "Source", "Global handler",
            "Error", "Failed to send email"
        );

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @ExceptionHandler(MailException.class)
    public ResponseEntity<Map<String, String>> handleMailException(MailException ex) {
        logger.warn("The mail module is not available: {}", ex.getMessage(), ex);

        Map<String, String> response = Map.of(
            "Source", "Global handler",
            "Error", "Mail system error"
        );

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @ExceptionHandler(MessagingException.class)
    public ResponseEntity<Map<String, String>> handleMessagingException(MessagingException ex) {
        logger.warn("MIME or SMTP error:{}", ex.getMessage(), ex);

        Map<String, String> response = Map.of(
            "Source", "Global handler",
            "Error", "Email formatting or transport error"
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }


}

