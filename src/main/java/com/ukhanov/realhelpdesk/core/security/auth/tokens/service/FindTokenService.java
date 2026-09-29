package com.ukhanov.realhelpdesk.core.security.auth.tokens.service;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ukhanov.realhelpdesk.core.security.auth.tokens.exception.TokenException;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.RefreshTokenModel;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.TokenBearer;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.repository.JwtRefreshTokenRepository;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.utils.TokenHasher;

@Service
public class FindTokenService {
    private static final Logger logger = LoggerFactory.getLogger(FindTokenService.class);

    private final JwtRefreshTokenRepository jwtRefreshTokenRepository;

    public FindTokenService(JwtRefreshTokenRepository jwtRefreshTokenRepository) {
        this.jwtRefreshTokenRepository = jwtRefreshTokenRepository;
    }

    // В БД хранится SHA-256 хеш токена
    public RefreshTokenModel findRefreshToken(TokenBearer token) throws TokenException {
        Objects.requireNonNull(token, "Токен не может быть null!");
        Objects.requireNonNull(token.getToken(), "Значение токена не может быть null!");

        RefreshTokenModel refreshTokenModel = jwtRefreshTokenRepository.findByTokenRefresh(TokenHasher.sha256(token.getToken()))
                .orElseThrow(() -> {
                    logger.error("Токен обновления не найден");
                    return new TokenException("Токен обновления не найден", null);
                });

        logger.debug("Токен обновления найден: id={}, статус={}", refreshTokenModel.getUuid(), refreshTokenModel.getStatus());
        return refreshTokenModel;
    }
}
