package com.ukhanov.realhelpdesk.core.config;

/**
 * Значения SameSite для auth-cookie — весь набор, который вообще допустим в ключе {@code jwt.cookie.same-site} (переменная
 * {@code JWT_COOKIE_SAMESITE}, см. application.properties и docker/app.env).
 *
 * <p>
 * Какой элемент подставить, решает биндинг Spring: строка из конфигурации превращается в элемент enum без учёта регистра, поэтому писать
 * можно {@code None}, {@code none} или {@code NONE}. Неизвестное значение валит старт приложения — опечатка не уйдёт в cookie. В заголовок
 * Set-Cookie уходит форма из {@link #getValue()}: строго {@code None}/{@code Lax}/{@code Strict}.
 * </p>
 */
public enum SameSite {

    /** Кросс-доменный фронт: браузер отправляет cookie и на чужие сайты. */
    NONE("None"),

    /** Чужой сайт получает cookie только на навигацию (переход по ссылке), не на запросы с другого origin. */
    LAX("Lax"),

    /** Cookie не уходит на чужие сайты вообще, даже при переходе по ссылке. */
    STRICT("Strict");

    private final String value;

    SameSite(String value) {
        this.value = value;
    }

    /** Значение для заголовка Set-Cookie. */
    public String getValue() {
        return value;
    }
}
