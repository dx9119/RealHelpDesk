package com.ukhanov.realhelpdesk.core.config;

import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Base64;
import java.util.Set;

@Component
public class JwtConfig {

    private final Key jwtKey;
    private final String issuer;
    private final Set<String> audience;
    private final Integer accessTokenExp;
    private final Integer refreshExp;

    public JwtConfig(
            @Value("${jwt.secret-for-gen-jwt}") String secretForGenJwt,
            @Value("${jwt.issuer}") String issuer,
            @Value("${jwt.audience}") Set<String> audience,
            @Value("${jwt.access-token-expiration}") Integer accessTokenExp,
            @Value("${jwt.refresh-token-expiration}") Integer refreshExp
    ) {
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
            throw new IllegalStateException(
                    "Ошибка при декодировании секрета JWT: должен быть корректной строкой в формате Base64 достаточной длины для генерации ключа HMAC (HS256 требует ключ длиной 256 бит).",
                    e
            );
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
