package com.ukhanov.realhelpdesk.core.annotation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NoHtmlValidator implements ConstraintValidator<NoHtml, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        // Регулярка: пытаемся запретить HTML, JS и SQL в ДТО
        boolean valid = !value.matches("(?i).*(<script>|</script>|<.*?>|SELECT\\s|INSERT\\s|UPDATE\\s|DELETE\\s|"
                + "--|\\/\\*|\\*\\/|DROP\\s|TRUNCATE\\s|EXEC\\s|UNION\\s).*");
        if (!valid) {
            // Имя поля валидатор не получает: какие поля отклонены, пишет WARN в GlobalExceptionHandler.
            // Содержимое значения не логируем — отклонённая разметка и есть это значение (audit/toLoggingUpdate.md, C6)
            logger.debug("Отклонена HTML/JS/SQL-разметка во входном значении");
        }

        return valid;
    }
}
