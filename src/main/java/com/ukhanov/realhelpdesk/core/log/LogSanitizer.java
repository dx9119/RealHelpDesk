package com.ukhanov.realhelpdesk.core.log;

import java.util.regex.Pattern;

/**
 * Маскирование секретных сегментов пути перед записью в лог. Код восстановления пароля и токен подтверждения email
 * передаются в пути запроса — в логах они должны заменяться на ***.
 */
public final class LogSanitizer {

    private static final Pattern SECRET_SEGMENT = Pattern.compile("(/password-resets/|/email/confirmations/)[^/]+");

    private LogSanitizer() {
    }

    public static String uri(String uri) {
        if (uri == null) {
            return "";
        }
        return SECRET_SEGMENT.matcher(uri).replaceAll("$1***");
    }
}
