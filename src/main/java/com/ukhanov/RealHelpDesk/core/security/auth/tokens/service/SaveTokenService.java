package com.ukhanov.RealHelpDesk.core.security.auth.tokens.service;

import com.ukhanov.RealHelpDesk.core.security.auth.tokens.model.RefreshTokenModel;
import com.ukhanov.RealHelpDesk.core.security.auth.tokens.repository.JwtRefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class SaveTokenService {
    private static final Logger logger = LoggerFactory.getLogger(SaveTokenService.class);

    private final JwtRefreshTokenRepository jwtRefreshTokenRepository;

    public SaveTokenService(JwtRefreshTokenRepository jwtRefreshTokenRepository) {
        this.jwtRefreshTokenRepository = jwtRefreshTokenRepository;
    }

    public RefreshTokenModel saveRefreshToken(RefreshTokenModel token) {
        Objects.requireNonNull(token, "RefreshTokenModel cannot be null");

        logger.info("Saving refresh token {}", token);
        return jwtRefreshTokenRepository.save(token);
    }
}
