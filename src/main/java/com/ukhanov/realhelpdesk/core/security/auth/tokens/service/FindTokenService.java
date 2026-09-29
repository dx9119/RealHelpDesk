package com.ukhanov.realhelpdesk.core.security.auth.tokens.service;

import com.ukhanov.realhelpdesk.core.security.auth.tokens.exception.TokenException;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.RefreshTokenModel;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.TokenBearer;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.repository.JwtRefreshTokenRepository;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.utils.TokenHasher;
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

    // В БД хранится SHA-256 хеш токена, поэтому ищем по хешу
    public RefreshTokenModel findRefreshToken(TokenBearer token) throws TokenException {
        Objects.requireNonNull(token, "Токен не может быть null!");
        Objects.requireNonNull(token.getToken(), "Значение токена не может быть null!");

        String hashedToken = TokenHasher.sha256(token.getToken());

        Optional<RefreshTokenModel> refreshTokenModel = jwtRefreshTokenRepository.findByTokenRefresh(hashedToken);

        if (refreshTokenModel.isEmpty()) {
            // Совместимость со строками, созданными до перехода на хранение хеша
            refreshTokenModel = jwtRefreshTokenRepository.findByTokenRefresh(token.getToken());
            if (refreshTokenModel.isPresent()) {
                RefreshTokenModel legacy = refreshTokenModel.get();
                legacy.setToken(hashedToken);
                jwtRefreshTokenRepository.save(legacy);
                logger.debug("Refresh-токен мигрирован на хранение хеша: id={}", legacy.getUuid());
            }
        }

        return refreshTokenModel
                .map(foundToken -> {
                    logger.debug("Токен обновления найден: id={}, статус={}", foundToken.getUuid(), foundToken.getStatus());
                    return foundToken;
                })
                .orElseThrow(() -> {
                    logger.error("Токен обновления не найден");
                    return new TokenException("Токен обновления не найден", null);
                });
    }
}
