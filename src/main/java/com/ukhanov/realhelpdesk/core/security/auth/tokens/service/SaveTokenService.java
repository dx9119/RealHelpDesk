package com.ukhanov.realhelpdesk.core.security.auth.tokens.service;

import com.ukhanov.realhelpdesk.core.security.auth.logout.exception.LogoutException;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.exception.TokenException;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.RefreshTokenModel;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.repository.JwtRefreshTokenRepository;
import com.ukhanov.realhelpdesk.core.security.user.SecurityUser;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
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
        Objects.requireNonNull(token, "RefreshTokenModel не может быть null");
        return jwtRefreshTokenRepository.save(token);
    }


}
