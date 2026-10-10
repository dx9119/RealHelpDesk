package com.ukhanov.realhelpdesk.core.security.auth.tokens.service;

import java.util.Objects;

import org.springframework.stereotype.Service;

import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.RefreshTokenModel;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.repository.JwtRefreshTokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class SaveTokenService {

    private final JwtRefreshTokenRepository jwtRefreshTokenRepository;

    public RefreshTokenModel saveRefreshToken(RefreshTokenModel token) {
        Objects.requireNonNull(token, "RefreshTokenModel не может быть null");
        return jwtRefreshTokenRepository.save(token);
    }

}
