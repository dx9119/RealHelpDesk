package com.ukhanov.realhelpdesk.core.security.auth.tokens.service;

import com.ukhanov.realhelpdesk.core.config.JwtConfig;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.TokenBearer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Objects;

@Service
public class DecodeTokenService {
    private static final Logger logger = LoggerFactory.getLogger(DecodeTokenService.class);

    private final JwtConfig jwtConfig;

    public DecodeTokenService(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
    }

    public Claims decodeJwtClaims(TokenBearer token) throws JwtException {
        Objects.requireNonNull(token, "Token cannot be null!");

        logger.debug("Parsing JWT claims for token");

        // Распарсиваем для логирования всех claim'ов
        Claims claims = Jwts.parser()
                .verifyWith((SecretKey) jwtConfig.getJwtKey())
                .build()
                .parseSignedClaims(token.getToken())
                .getPayload();

        // Обязательные поля
        if (claims.getSubject() == null) {
            throw new JwtException("Missing subject claim in token");
        }

        String role = claims.get("role", String.class);
        if (role == null) {
            throw new JwtException("Missing role claim in token: "+claims.get("role", String.class));
        }

        return claims;
    }

    public String[] decodeJwtForAuth(TokenBearer token) throws JwtException {
        Claims claims = decodeJwtClaims(token);

        String userId = claims.getSubject();
        String role = claims.get("role", String.class);

        return new String[] { userId, role };
    }


}
