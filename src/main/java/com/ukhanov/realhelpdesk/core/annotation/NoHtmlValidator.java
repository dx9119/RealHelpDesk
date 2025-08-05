package com.ukhanov.realhelpdesk.core.annotation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NoHtmlValidator implements ConstraintValidator<NoHtml, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;

        // Регулярка: запрещаем HTML, JS и SQL
        return !value.matches("(?i).*(<script>|</script>|<.*?>|SELECT\\s|INSERT\\s|UPDATE\\s|DELETE\\s|--|\\/\\*|\\*\\/|DROP\\s|TRUNCATE\\s|EXEC\\s|UNION\\s).*");
    }
}
