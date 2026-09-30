package com.ukhanov.realhelpdesk.core.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import io.jsonwebtoken.JwtException;

@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException ex, HttpServletRequest request) {
        logger.warn("Ошибка API {}: {}", ex.getStatus().value(), ex.getMessage());
        return ProblemResponses.entity(ex.getStatus(), ex.getMessage(), request);
    }

    @ExceptionHandler(ApiRuntimeException.class)
    public ResponseEntity<ProblemDetail> handleApiRuntimeException(ApiRuntimeException ex, HttpServletRequest request) {
        logger.warn("Ошибка API {}: {}", ex.getStatus().value(), ex.getMessage());
        return ProblemResponses.entity(ex.getStatus(), ex.getMessage(), request);
    }

    @ExceptionHandler({UsernameNotFoundException.class, BadCredentialsException.class})
    public ResponseEntity<ProblemDetail> handleAuthenticationFailure(Exception ex, HttpServletRequest request) {
        logger.warn("Ошибка аутентификации: {}", ex.getMessage());
        return ProblemResponses.entity(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAuthorizationDenied(AuthorizationDeniedException ex, HttpServletRequest request) {
        logger.warn("Доступ запрещен: {}", ex.getMessage());
        return ProblemResponses.entity(HttpStatus.FORBIDDEN, "Доступ запрещен", request);
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ProblemDetail> handleJwtException(JwtException ex, HttpServletRequest request) {
        logger.warn("JWT ошибка: {}", ex.getMessage());
        return ProblemResponses.entity(HttpStatus.UNAUTHORIZED, "Некорректный или недействительный токен", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        logger.warn("Ошибка валидации тела запроса: {}", request.getRequestURI());

        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            if (error instanceof FieldError fieldError) {
                errors.put(fieldError.getField(), fieldError.getDefaultMessage());
            } else {
                errors.put(error.getObjectName(), error.getDefaultMessage());
            }
        });

        return ProblemResponses.entity(HttpStatus.BAD_REQUEST, "Некорректный запрос", request, errors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        logger.warn("Некорректное значение параметра '{}': {}", ex.getName(), ex.getValue());

        String expected = ex.getRequiredType() != null ? "Ожидаемый тип: " + ex.getRequiredType().getSimpleName() + ". " : "";
        Map<String, String> errors = Map.of(ex.getName(), expected + "Получено: " + ex.getValue());

        return ProblemResponses.entity(HttpStatus.BAD_REQUEST, "Некорректный параметр запроса", request, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        logger.warn("Нарушение ограничений запроса: {}", ex.getConstraintViolations().size());

        Map<String, String> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            errors.put(violation.getPropertyPath().toString(), violation.getMessage());
        }

        return ProblemResponses.entity(HttpStatus.BAD_REQUEST, "Некорректный запрос", request, errors);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        logger.warn("Отсутствует обязательный параметр '{}'", ex.getParameterName());
        return ProblemResponses.entity(HttpStatus.BAD_REQUEST, "Отсутствует обязательный параметр '" + ex.getParameterName() + "'",
                request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableMessage(HttpMessageNotReadableException ex, HttpServletRequest request) {
        logger.warn("Нечитаемое тело запроса {}: {}", request.getRequestURI(), ex.getMessage());
        return ProblemResponses.entity(HttpStatus.BAD_REQUEST, "Некорректное тело запроса", request);
    }

    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ProblemDetail> handlePropertyReference(PropertyReferenceException ex, HttpServletRequest request) {
        logger.warn("Недопустимое поле сортировки: {}", ex.getPropertyName());
        return ProblemResponses.entity(HttpStatus.BAD_REQUEST, "Недопустимое поле сортировки: " + ex.getPropertyName(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        logger.warn("Метод {} не поддерживается для {}", ex.getMethod(), request.getRequestURI());
        return ProblemResponses.entity(HttpStatus.METHOD_NOT_ALLOWED, "Метод " + ex.getMethod() + " не поддерживается", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        logger.warn("Неподдерживаемый тип содержимого: {}", ex.getContentType());
        return ProblemResponses.entity(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Неподдерживаемый тип содержимого", request);
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ProblemDetail> handleNoHandler(Exception ex, HttpServletRequest request) {
        logger.warn("Маршрут не найден: {} {}", request.getMethod(), request.getRequestURI());
        return ProblemResponses.entity(HttpStatus.NOT_FOUND, "Ресурс не найден", request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLocking(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        logger.warn("Конфликт версий при сохранении объекта: {}", ex.getMessage());
        return ProblemResponses.entity(HttpStatus.CONFLICT, "Конфликт версий, проверьте актуальность данных и попробуйте снова", request);
    }

    @ExceptionHandler(MailSendException.class)
    public ResponseEntity<ProblemDetail> handleMailSendException(MailSendException ex, HttpServletRequest request) {
        Throwable rootCause = ex.getCause();
        logger.error("Ошибка при отправке email: {}", rootCause != null ? rootCause.getMessage() : ex.getMessage(), ex);
        return ProblemResponses.entity(HttpStatus.SERVICE_UNAVAILABLE, "Ошибка отправки email", request);
    }

    @ExceptionHandler(MailException.class)
    public ResponseEntity<ProblemDetail> handleMailException(MailException ex, HttpServletRequest request) {
        logger.error("Почтовый модуль недоступен: {}", ex.getMessage(), ex);
        return ProblemResponses.entity(HttpStatus.SERVICE_UNAVAILABLE, "Почтовая система столкнулась с ошибкой", request);
    }

    @ExceptionHandler(MessagingException.class)
    public ResponseEntity<ProblemDetail> handleMessagingException(MessagingException ex, HttpServletRequest request) {
        logger.error("MIME или SMTP ошибка: {}", ex.getMessage(), ex);
        return ProblemResponses.entity(HttpStatus.SERVICE_UNAVAILABLE, "Ошибка отправки email", request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> handleResponseStatusException(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        HttpStatus resolved = status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
        String detail = ex.getReason() != null ? ex.getReason() : resolved.getReasonPhrase();
        logger.warn("Ошибка ответа {}: {}", resolved.value(), detail);
        return ProblemResponses.entity(resolved, detail, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex, HttpServletRequest request) {
        logger.error("Перехвачено необработанное исключение: {}", ex.getMessage(), ex);
        return ProblemResponses.entity(HttpStatus.INTERNAL_SERVER_ERROR, "Операция завершилась неудачей", request);
    }
}
