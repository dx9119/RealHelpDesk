package com.ukhanov.RealHelpDesk.core.security.auth.tokens.service;

import com.ukhanov.RealHelpDesk.core.config.JwtConfig;
import com.ukhanov.RealHelpDesk.core.security.auth.tokens.dto.TokenBearerResponse;
import com.ukhanov.RealHelpDesk.core.security.auth.tokens.model.RefreshTokenModel;
import com.ukhanov.RealHelpDesk.core.security.auth.tokens.model.TokenStatus;
import com.ukhanov.RealHelpDesk.core.security.user.SecurityUser;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;

@Service
public class GenTokenService {
    private static final Logger logger = LoggerFactory.getLogger(GenTokenService.class);

    private final JwtConfig jwtConfig;

    public GenTokenService(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
    }

    public TokenBearerResponse generateAccessJwtToken(SecurityUser securityUser) {
        Objects.requireNonNull(securityUser, "SecurityUser cannot be null!");

        Instant dateNow = Instant.now();
        Instant expiry = dateNow.plusSeconds(jwtConfig.getAccessTokenExp() * 60L);

        logger.debug("Start gen access token for: {}, role user: {}", securityUser.getUsername(), securityUser.getRule());

        String token = Jwts.builder()
                .issuer(jwtConfig.getIssuer())
                .subject(securityUser.getId())
                .expiration(Date.from(expiry))
                .issuedAt(Date.from(dateNow))
                .claim("role", securityUser.getRule())
                .claim("aud", jwtConfig.getAudience()) //вместо audience().add
                .signWith((SecretKey) jwtConfig.getJwtKey())
                .compact();


        TokenBearerResponse accessTokenModel = new TokenBearerResponse();
        accessTokenModel.setToken(token);
        logger.debug("Final gen access token for: {}", accessTokenModel.getToken());
        return accessTokenModel;
    }

    public RefreshTokenModel generateRefreshJwtToken(SecurityUser securityUser) {
        Objects.requireNonNull(securityUser, "SecurityUser cannot be null!");

        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(jwtConfig.getRefreshExpiration() * 60L);

        logger.debug("Start gen refresh Token for user {}", securityUser.getUsername());
        String token = Jwts.builder()
                .issuer(jwtConfig.getIssuer())
                .subject(securityUser.getId())
                .audience().add(jwtConfig.getAudience()).and()
                .expiration(Date.from(expiry))
                .issuedAt(Date.from(now))
                .signWith((SecretKey) jwtConfig.getJwtKey())
                .compact();

        RefreshTokenModel jwtRefreshTokenModel = new RefreshTokenModel();
        jwtRefreshTokenModel.setUser(securityUser.getOriginalUser());
        jwtRefreshTokenModel.setStatus(TokenStatus.ACTIVE);
        jwtRefreshTokenModel.setToken(token);

        logger.debug("Final gen refresh Token for user {}", jwtRefreshTokenModel.getToken());
        return jwtRefreshTokenModel;
    }

}
