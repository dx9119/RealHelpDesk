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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private ResponseEntity<Map<String, String>> buildResponse(HttpStatus status, String errorMessage) {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("Источник", "Глобальный перехватчик");
        response.put("Ошибка", errorMessage);
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> handleConstraintViolationException(ConstraintViolationException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;

        Map<String, String> errors = new LinkedHashMap<>();
        errors.put("Источник", "Глобальный перехватчик");

        logger.error("Глобальный перехватчик сработал: {}", ex.getConstraintViolations().size());
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            errors.put(violation.getPropertyPath().toString(), violation.getMessage());
        }

        return new ResponseEntity<>(errors, status);
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAuthorizationDeniedException(AuthorizationDeniedException ex) {
        logger.warn("Доступ запрещен: {}", ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, "Доступ запрещен");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex) {
        logger.error("Перехвачено необработанное исключение: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Операция завершилась неудачей");
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException ex) {
        logger.warn("Конфликт версий при сохранении объекта: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, "Конфликт версий, проверьте актуальность данных и попробуйте снова");
    }

    @ExceptionHandler(MailSendException.class)
    public ResponseEntity<Map<String, String>> handleMailSendException(MailSendException ex) {
        Throwable rootCause = ex.getCause();
        String causeMessage = rootCause != null ? rootCause.getMessage() : "Unknown mail cause";
        logger.error("Ошибка при отправке email: {}", causeMessage, ex);
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "Ошибка отправки email");
    }

    @ExceptionHandler(MailException.class)
    public ResponseEntity<Map<String, String>> handleMailException(MailException ex) {
        logger.error("Почтовый модуль недоступен: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "Почтовая система столкнулась с ошибкой");
    }

    @ExceptionHandler(MessagingException.class)
    public ResponseEntity<Map<String, String>> handleMessagingException(MessagingException ex) {
        logger.error("MIME или SMTP ошибка: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.BAD_REQUEST, "Ошибка MIME или SMTP");
    }
}
