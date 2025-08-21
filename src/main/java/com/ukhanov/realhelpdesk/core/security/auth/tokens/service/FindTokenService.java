package com.ukhanov.realhelpdesk.core.security.auth.tokens.service;

import com.ukhanov.realhelpdesk.core.security.auth.tokens.exception.TokenException;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.RefreshTokenModel;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.TokenBearer;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.repository.JwtRefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
public class FindTokenService {
    private static final Logger logger = LoggerFactory.getLogger(FindTokenService.class);

    private final JwtRefreshTokenRepository jwtRefreshTokenRepository;

    public FindTokenService(JwtRefreshTokenRepository jwtRefreshTokenRepository) {
        this.jwtRefreshTokenRepository = jwtRefreshTokenRepository;
    }

    public RefreshTokenModel findRefreshToken(TokenBearer token) throws TokenException {
        Objects.requireNonNull(token, "Токен не может быть null!");
        Objects.requireNonNull(token.getToken(), "Значение токена не может быть null!");

        Optional<RefreshTokenModel> refreshTokenModel = jwtRefreshTokenRepository.findByTokenRefresh(token.getToken());
        return refreshTokenModel
                .map(foundToken -> {
                    logger.debug("Токен обновления найден: {}", foundToken.getToken());
                    return foundToken;
                })
                .orElseThrow(() -> {
                    logger.error("Токен обновления не найден: {}", token.getToken());
                    return new TokenException("Токен обновления не найден: " + token.getToken(), null);
                });
    }
}
