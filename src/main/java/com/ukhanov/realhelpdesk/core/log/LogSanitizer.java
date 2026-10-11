package com.ukhanov.realhelpdesk.core.log;

import java.util.regex.Pattern;

/**
 * Маскирование секретных сегментов пути перед записью в лог. Код восстановления пароля и токен подтверждения email передаются в пути
 * запроса — в логах они должны заменяться на ***.
 *
 * <p>
 * {@link #value(String)} готовит к логу значение из внешних данных (заголовок, метаданные вложения): перевод строки в логе ломает разбор по
 * колонкам, а неограниченная длина раздувает строку.
 * </p>
 */
public final class LogSanitizer {

    private static final Pattern SECRET_SEGMENT = Pattern.compile("(/password-resets/|/email/confirmations/)[^/]+");
    private static final int MAX_VALUE_LENGTH = 120;

    private LogSanitizer() {
    }

    public static String uri(String uri) {
        if (uri == null) {
            return "";
        }
        return SECRET_SEGMENT.matcher(uri).replaceAll("$1***");
    }

    /** Значение из внешних данных для лога: управляющие символы заменяются, длинное значение обрезается. */
    public static String value(String value) {
        if (value == null) {
            return "";
        }
        String flat = value.replaceAll("[\\r\\n\\t]", "_");
        return flat.length() <= MAX_VALUE_LENGTH ? flat : flat.substring(0, MAX_VALUE_LENGTH) + "…";
    }
}
