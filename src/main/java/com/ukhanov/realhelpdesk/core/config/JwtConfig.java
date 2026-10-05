package com.ukhanov.realhelpdesk.core.config;

import java.security.Key;
import java.util.Base64;
import java.util.Set;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.security.Keys;

/**
 * Готовый к работе конфиг JWT: проверяет настройки из {@link JwtProperties} и собирает ключ подписи. Падает на старте с понятной ошибкой,
 * если секрет или адреса не заданы.
 */
@Component
public class JwtConfig {

    private final Key jwtKey;
    private final String issuer;
    private final Set<String> audience;
    private final Integer accessTokenExp;
    private final Integer refreshExp;

    public JwtConfig(JwtProperties properties) {
        String secretForGenJwt = properties.getSecretForGenJwt();
        String issuer = properties.getIssuer();
        Set<String> audience = properties.getAudience();
        Integer accessTokenExp = properties.getAccessTokenExpiration();
        Integer refreshExp = properties.getRefreshTokenExpiration();

        if (secretForGenJwt == null || secretForGenJwt.isBlank()) {
            throw new IllegalArgumentException("jwt.secret-for-gen-jwt не содержит значение");
        }

        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("jwt.issuer не содержит значение");
        }

        if (audience == null || audience.isEmpty()) {
            throw new IllegalArgumentException("jwt.audience не содержит значение");
        }

        if (accessTokenExp == null || accessTokenExp <= 0) {
            throw new IllegalArgumentException("jwt.access-token-expiration не содержит значение");
        }

        if (refreshExp == null || refreshExp <= 0) {
            throw new IllegalArgumentException("jwt.refresh-token-expiration не содержит значение");
        }

        try {
            this.jwtKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secretForGenJwt));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Ошибка при декодировании секрета JWT: должен быть корректной строкой в формате Base64 "
                    + "достаточной длины для генерации ключа HMAC (HS256 требует ключ длиной 256 бит).", e);
        }

        this.issuer = issuer;
        this.audience = audience;
        this.accessTokenExp = accessTokenExp;
        this.refreshExp = refreshExp;
    }

    public Key getJwtKey() {
        return jwtKey;
    }

    public String getIssuer() {
        return issuer;
    }

    public Set<String> getAudience() {
        return audience;
    }

    public Integer getAccessTokenExp() {
        return accessTokenExp;
    }

    public Integer getRefreshExpiration() {
        return refreshExp;
    }
}
