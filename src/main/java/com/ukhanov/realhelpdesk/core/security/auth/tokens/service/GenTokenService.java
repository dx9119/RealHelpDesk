package com.ukhanov.realhelpdesk.core.security.auth.tokens.service;

import com.ukhanov.realhelpdesk.core.config.JwtConfig;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.dto.TokenBearerResponse;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.RefreshTokenModel;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.TokenStatus;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.utils.JwtClaims;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.utils.TokenHasher;
import com.ukhanov.realhelpdesk.core.security.user.SecurityUser;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

@Service
public class GenTokenService {
    private static final Logger logger = LoggerFactory.getLogger(GenTokenService.class);

    private final JwtConfig jwtConfig;

    public GenTokenService(JwtConfig jwtConfig) {
        this.jwtConfig = jwtConfig;
    }

    public TokenBearerResponse generateAccessJwtToken(SecurityUser securityUser) {
        Objects.requireNonNull(securityUser, "SecurityUser не может быть null!");
        Objects.requireNonNull(securityUser.getRule(), "Роль у SecurityUser не может отсутствовать!");

        Instant dateNow = Instant.now();
        Instant expiry = dateNow.plusSeconds(jwtConfig.getAccessTokenExp() * 60L);

        String token = Jwts.builder()
                .issuer(jwtConfig.getIssuer())
                .subject(securityUser.getId())
                // jti гарантирует уникальность: два токена, выданные в одну секунду, иначе байт-в-байт одинаковы
                .id(UUID.randomUUID().toString())
                .expiration(Date.from(expiry))
                .issuedAt(Date.from(dateNow))
                .claim(JwtClaims.TYPE, JwtClaims.TYPE_ACCESS)
                .claim(JwtClaims.TOKEN_VERSION, securityUser.getOriginalUser().getTokenVersion())
                .claim(JwtClaims.ROLE, securityUser.getRule())
                .claim("aud", jwtConfig.getAudience()) //вместо audience().add
                .signWith((SecretKey) jwtConfig.getJwtKey())
                .compact();


        TokenBearerResponse accessTokenModel = new TokenBearerResponse();
        accessTokenModel.setToken(token);
        return accessTokenModel;
    }

    public RefreshTokenModel generateRefreshJwtToken(SecurityUser securityUser) {
        Objects.requireNonNull(securityUser, "SecurityUser не может быть null!");

        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(jwtConfig.getRefreshExpiration() * 60L);

        String token = Jwts.builder()
                .issuer(jwtConfig.getIssuer())
                .subject(securityUser.getId())
                // Уникальный id обязателен: в БД хеш токена хранится с unique-ограничением
                .id(UUID.randomUUID().toString())
                .audience().add(jwtConfig.getAudience()).and()
                .expiration(Date.from(expiry))
                .issuedAt(Date.from(now))
                .claim(JwtClaims.TYPE, JwtClaims.TYPE_REFRESH)
                .signWith((SecretKey) jwtConfig.getJwtKey())
                .compact();

        RefreshTokenModel jwtRefreshTokenModel = new RefreshTokenModel();
        jwtRefreshTokenModel.setUser(securityUser.getOriginalUser());
        jwtRefreshTokenModel.setStatus(TokenStatus.ACTIVE);
        // В БД — только хеш, сырой токен отдаём клиенту через rawToken
        jwtRefreshTokenModel.setToken(TokenHasher.sha256(token));
        jwtRefreshTokenModel.setRawToken(token);

        return jwtRefreshTokenModel;
    }

}
