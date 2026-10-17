package com.ukhanov.realhelpdesk.core.security.localjwt.config;

import java.security.Key;
import java.util.Base64;
import java.util.Set;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.security.Keys;
import lombok.Getter;

/**
 * Готовый к работе конфиг JWT: проверяет настройки из {@link JwtProperties} и собирает ключ подписи. Падает на старте с понятной ошибкой,
 * если секрет или адреса не заданы.
 *
 * <p>
 * Профиль {@code keycloak} его не отключает: выдачу и отзыв токенов локального контура (register/login/refresh) собирают
 * {@code GenTokenService}, {@code GetTokenService} и {@code RefreshTokenCleanupService} — все они зависят от этого бина. В keycloak-контуре
 * входящие токены проверяет {@code KeycloakSecurityConfiguration}, поэтому {@code JwtConfig} остаётся неработающим «нагрузочным» конфигом,
 * а не точкой отказа.
 * </p>
 */
@Component
@Getter
public class JwtConfig {

    private final Key jwtKey;
    private final String issuer;
    private final Set<String> audience;
    private final Integer accessTokenExpirationSeconds;
    private final Integer refreshTokenExpirationSeconds;

    public JwtConfig(JwtProperties properties) {
        String secretForGenJwt = properties.getSecretForGenJwt();
        String issuer = properties.getIssuer();
        Set<String> audience = properties.getAudience();
        Integer accessExpSeconds = properties.getAccessTokenExpirationSeconds();
        Integer refreshExpSeconds = properties.getRefreshTokenExpirationSeconds();

        if (secretForGenJwt == null || secretForGenJwt.isBlank()) {
            throw new IllegalArgumentException("jwt.secret-for-gen-jwt не содержит значение");
        }

        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("jwt.issuer не содержит значение");
        }

        if (audience == null || audience.isEmpty()) {
            throw new IllegalArgumentException("jwt.audience не содержит значение");
        }

        if (accessExpSeconds == null || accessExpSeconds <= 0) {
            throw new IllegalArgumentException("jwt.access-token-expiration-seconds не содержит значение");
        }

        if (refreshExpSeconds == null || refreshExpSeconds <= 0) {
            throw new IllegalArgumentException("jwt.refresh-token-expiration-seconds не содержит значение");
        }

        try {
            this.jwtKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secretForGenJwt));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Ошибка при декодировании секрета JWT: должен быть корректной строкой в формате Base64 "
                    + "достаточной длины для генерации ключа HMAC (HS256 требует ключ длиной 256 бит).", e);
        }

        this.issuer = issuer;
        this.audience = audience;
        this.accessTokenExpirationSeconds = accessExpSeconds;
        this.refreshTokenExpirationSeconds = refreshExpSeconds;
    }
}
