package com.ukhanov.realhelpdesk.core.security.auth.tokens.service;

import com.ukhanov.realhelpdesk.core.config.JwtConfig;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.exception.TokenException;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.TokenBearer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
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

        return claims;
    }

    public String[] decodeJwtForAuth(TokenBearer token) throws JwtException {
        Claims claims = decodeJwtClaims(token);

        String userId = claims.getSubject();
        String role = claims.get("role", String.class);

        return new String[] { userId, role };
    }

    public String extractTokenFromCookies(HttpServletRequest request, String cookieName)
        throws TokenException {
        logger.debug("Start extracting cookie. Requested name: '{}'", cookieName);

        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            logger.warn("No cookies received in request");
            throw new TokenException("No cookies received in request");
        }

        logger.debug("Number of cookies received: {}", cookies.length);

        for (int i = 0; i < cookies.length; i++) {
            Cookie cookie = cookies[i];
            logger.info("Cookie[{}]: name='{}', value='{}'", i, cookie.getName(), cookie.getValue());
        }

        boolean found = false;
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                logger.info("Target cookie '{}' found. Returning value: '{}'", cookieName, cookie.getValue());
                found = true;
                return cookie.getValue();
            }
        }

      throw new TokenException("No cookie with name '" + cookieName + "' found in request");

    }

}
