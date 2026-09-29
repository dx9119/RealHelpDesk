package com.ukhanov.realhelpdesk.core.security.auth.tokens.utils;

// Имена claim'ов, используемых в JWT проекта
public final class JwtClaims {

    // Тип токена: access-токен нельзя использовать как refresh и наоборот
    public static final String TYPE = "typ";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    // Версия access-токенов пользователя (отзыв всех выданных токенов)
    public static final String TOKEN_VERSION = "ver";

    public static final String ROLE = "role";

    private JwtClaims() {
    }
}
