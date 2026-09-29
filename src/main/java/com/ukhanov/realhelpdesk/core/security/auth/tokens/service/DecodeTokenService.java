package com.ukhanov.realhelpdesk.core.security.auth.tokens.service;

import com.ukhanov.realhelpdesk.core.config.JwtConfig;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.exception.TokenException;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.TokenBearer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
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
        Objects.requireNonNull(token, "Токен не может быть null!");

        // jjwt на пустой строке бросает IllegalArgumentException — нормализуем в JwtException (обрабатывается фильтром)
        if (token.getToken() == null || token.getToken().isBlank()) {
            throw new MalformedJwtException("Токен отсутствует или пуст");
        }

        Claims claims = Jwts.parser()
                .verifyWith((SecretKey) jwtConfig.getJwtKey())
                .build()
                .parseSignedClaims(token.getToken())
                .getPayload();

        // Обязательные поля
        if (claims.getSubject() == null) {
            throw new JwtException("Отсутствует claim 'subject' в токене");
        }

        return claims;
    }

    public String extractTokenFromCookies(HttpServletRequest request, String cookieName)
        throws TokenException {
        logger.debug("Начало извлечения cookie. Запрошенное имя: '{}'", cookieName);

        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            logger.warn("В запросе не получены cookies");
            throw new TokenException("В запросе не получены cookies");
        }

        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                logger.debug("Целевая cookie '{}' найдена", cookieName);
                return cookie.getValue();
            }
        }
        throw new TokenException("В запросе не найдена cookie с именем '" + cookieName + "'");
    }
}
