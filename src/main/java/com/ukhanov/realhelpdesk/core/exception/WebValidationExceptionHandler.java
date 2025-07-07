package com.ukhanov.realhelpdesk.core.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
@Order(1)
public class WebValidationExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(WebValidationExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, WebRequest request) {
        logger.debug("Global handler caught MethodArgumentNotValidException for path: {}", request.getDescription(false));

        Map<String, String> validationErrors = new HashMap<>();
        // Проходимся по всем ошибкам валидации и собираем их в Map
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            validationErrors.put(fieldName, errorMessage);
            logger.debug("Validation error for field '{}': {}", fieldName, errorMessage);
        });

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("timestamp", Instant.now().toString());
        responseBody.put("status", HttpStatus.BAD_REQUEST.value());
        responseBody.put("error", "Bad Request");
        responseBody.put("message", "Validation failed.");
        responseBody.put("errors", validationErrors);
        responseBody.put("path", request.getDescription(false).replace("uri=", ""));

        return new ResponseEntity<>(responseBody, HttpStatus.BAD_REQUEST);
    }
}
