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
            throw new IllegalArgumentException("jwt.secret-for-gen-jwt must not be null or blank");
        }

        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("jwt.issuer must not be null or blank");
        }

        if (audience == null || audience.isEmpty()) {
            throw new IllegalArgumentException("jwt.audience must not be null or empty");
        }

        if (accessTokenExp == null || accessTokenExp <= 0) {
            throw new IllegalArgumentException("jwt.access-token-expiration must be a positive number");
        }

        if (refreshExp == null || refreshExp <= 0) {
            throw new IllegalArgumentException("jwt.refresh-token-expiration must be a positive number");
        }

        try {
            this.jwtKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secretForGenJwt));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Failed to decode JWT secret: must be a valid Base64-encoded string of sufficient length for HMAC key generation (e.g. HS256 requires 256-bit key). Hint: use `openssl rand -base64 32` to generate one.",
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
