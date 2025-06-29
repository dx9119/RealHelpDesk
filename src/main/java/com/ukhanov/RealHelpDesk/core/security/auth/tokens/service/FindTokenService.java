package com.ukhanov.RealHelpDesk.core.security.auth.tokens.service;

import com.ukhanov.RealHelpDesk.core.security.auth.tokens.exception.TokenException;
import com.ukhanov.RealHelpDesk.core.security.auth.tokens.model.RefreshTokenModel;
import com.ukhanov.RealHelpDesk.core.security.auth.tokens.model.TokenBearer;
import com.ukhanov.RealHelpDesk.core.security.auth.tokens.repository.JwtRefreshTokenRepository;
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
        Objects.requireNonNull(token, "Token cannot be null!");
        logger.debug("Start finding refresh token: {}", token.getToken());

        Optional<RefreshTokenModel> refreshTokenModel =
                jwtRefreshTokenRepository.findByTokenRefresh(token.getToken());

        return refreshTokenModel
                .map(foundToken -> {
                    logger.debug("Refresh token found: {}", foundToken.getToken());
                    return foundToken;
                })
                .orElseThrow(() -> {
                    logger.error("Refresh token not found: {}", token.getToken());
                    return new TokenException("Refresh token not found",null);
                });
    }

}
