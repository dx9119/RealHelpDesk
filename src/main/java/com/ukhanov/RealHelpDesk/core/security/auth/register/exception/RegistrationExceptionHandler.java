package com.ukhanov.RealHelpDesk.core.security.auth.register.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.HashMap;
import java.util.Map;


@ControllerAdvice
@Order(1)
public class RegistrationExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(RegistrationExceptionHandler.class);

    @ExceptionHandler(RegistrationException.class)
    public ResponseEntity<Map<String, String>> handleRegistrationException(RegistrationException e, WebRequest webRequest) {
        HttpStatus status = HttpStatus.CONFLICT;

        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("Error:", e.getMessage());
        errorResponse.put("Cause:", e.getCause().getMessage());
        errorResponse.put("Path:", webRequest.getDescription(false));

        return new ResponseEntity<>(errorResponse, status);
    }

}
