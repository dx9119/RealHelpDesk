package com.ukhanov.realhelpdesk.core.security.auth.tokens.service;

import com.ukhanov.realhelpdesk.core.config.JwtConfig;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.exception.TokenException;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.TokenBearer;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.Set;

@Service
public class ValidTokenService {

    private static final Logger logger = LoggerFactory.getLogger(ValidTokenService.class);

    private final JwtConfig jwtConfig;
    private final DecodeTokenService decodeTokenService;

    public ValidTokenService(JwtConfig jwtConfig,DecodeTokenService getTokenService1) {
        this.jwtConfig = jwtConfig;
        this.decodeTokenService = getTokenService1;
    }

    public void lowLevelVerifyToken(TokenBearer token) throws TokenException {
        logger.debug("Start verifying token");

        Claims claims = decodeTokenService.decodeJwtClaims(token);
        logger.debug("Token claims: {}", claims.getExpiration());

        Instant expiration = Optional.ofNullable(claims.getExpiration())
                .map(Date::toInstant)
                .orElseThrow(() -> new TokenException("Expiration claim is missing", null));


        Set<String> actual = claims.getAudience();
        if (!jwtConfig.getAudience().equals(actual)) {
            logger.debug("Invalid audience: {}", actual);
            throw new TokenException("Audience mismatch", null);
        }


        if (expiration.isBefore(Instant.now())) {
            logger.debug("Token expired at {}", expiration);
            throw new TokenException("Token expired", null);
        }

        String tokenIssuer = claims.getIssuer();
        if (!jwtConfig.getIssuer().equals(tokenIssuer)) {
            logger.debug("Invalid issuer: {}", tokenIssuer);
            throw new TokenException("Invalid issuer", null);
        }
        logger.debug("Token verified");
    }
}
