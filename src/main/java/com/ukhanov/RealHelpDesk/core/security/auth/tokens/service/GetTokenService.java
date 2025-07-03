package com.ukhanov.RealHelpDesk.core.security.auth.tokens.service;

import com.ukhanov.RealHelpDesk.core.security.auth.tokens.dto.TokenBearerResponse;
import com.ukhanov.RealHelpDesk.core.security.auth.tokens.dto.TokenStatusResponse;
import com.ukhanov.RealHelpDesk.core.security.auth.tokens.dto.TokensResponse;
import com.ukhanov.RealHelpDesk.core.security.auth.tokens.exception.TokenException;
import com.ukhanov.RealHelpDesk.core.security.auth.tokens.model.RefreshTokenModel;
import com.ukhanov.RealHelpDesk.core.security.auth.tokens.model.TokenBearer;
import com.ukhanov.RealHelpDesk.core.security.auth.tokens.model.TokenStatus;
import com.ukhanov.RealHelpDesk.core.security.auth.tokens.repository.JwtRefreshTokenRepository;
import com.ukhanov.RealHelpDesk.core.security.user.SecurityUser;
import com.ukhanov.RealHelpDesk.core.security.user.model.UserModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class GetTokenService {
    private static final Logger logger = LoggerFactory.getLogger(GetTokenService.class);

    private final GenTokenService genTokenService;
    private final SaveTokenService saveTokenService;
    private final JwtRefreshTokenRepository jwtRefreshTokenRepository;
    private final FindTokenService findTokenService;
    private final ValidTokenService validTokenService;

    public GetTokenService(GenTokenService genTokenService, SaveTokenService saveTokenService, JwtRefreshTokenRepository jwtRefreshTokenRepository, FindTokenService findTokenService, ValidTokenService validTokenService) {
        this.genTokenService = genTokenService;
        this.saveTokenService = saveTokenService;
        this.jwtRefreshTokenRepository = jwtRefreshTokenRepository;
        this.findTokenService = findTokenService;
        this.validTokenService = validTokenService;
    }

    // Получить активный рефреш токен и новый access токен
    public TokensResponse getActiveTokens(UserModel userModel) throws TokenException {
        logger.debug("Getting active tokens for user: {}", userModel.getEmail());
        SecurityUser securityUser = new SecurityUser(userModel);

        TokenBearerResponse tokenBearerResponse = genTokenService.generateAccessJwtToken(securityUser);
        RefreshTokenModel refreshToken = getActiveRefreshToken(userModel);

        if(refreshToken.getToken() == null) {
            refreshToken = genTokenService.generateRefreshJwtToken(securityUser);
            logger.debug("not found refresh token, user: {}", securityUser.getUsername());
            saveTokenService.saveRefreshToken(refreshToken);
        }

        return TokensResponse.builder()
                .accessToken(tokenBearerResponse.getToken())
                .refreshToken(refreshToken.getToken())
                .message(securityUser.getUsername())
                .build();
    }

    // Получить новые токены
    public TokensResponse getNewTokens(UserModel user) {
        logger.debug("Generating new tokens for user: {}", user.getEmail());
        SecurityUser securityUser = new SecurityUser(user);

        TokenBearerResponse tokenBearerResponse = genTokenService.generateAccessJwtToken(securityUser);
        RefreshTokenModel refreshToken = genTokenService.generateRefreshJwtToken(securityUser);

        saveTokenService.saveRefreshToken(refreshToken);

        return TokensResponse.builder()
                .accessToken(tokenBearerResponse.getToken())
                .refreshToken(refreshToken.getToken())
                .message(securityUser.getUsername())
                .build();
    }

    public RefreshTokenModel getActiveRefreshToken(UserModel user) throws TokenException {
        Objects.requireNonNull(user, "user cannot be null!");

        return jwtRefreshTokenRepository
                .findTopByUserEmailAndStatusOrderByCreatedAtDesc(user.getEmail(), TokenStatus.ACTIVE)
                .orElseThrow(() -> new TokenException("Active refresh token not found"));
    }



    public TokenStatusResponse getStatusRefreshToken(TokenBearer tokenRefresh) throws TokenException {
        Objects.requireNonNull(tokenRefresh, "Token cannot be null");

        logger.debug("Start searching refresh tokenRefresh");
        RefreshTokenModel refreshToken = findTokenService.findRefreshToken(tokenRefresh);
        logger.debug("Found refresh tokenRefresh: {}", refreshToken);

        return TokenStatusResponse.builder()
                .tokenStatus(refreshToken.getStatus())
                .createdAt(refreshToken.getCreatedAt())
                .build();
    }

    public UserModel getUserByRefreshToken(String tokenRefresh) throws TokenException {
        Objects.requireNonNull(tokenRefresh, "Token cannot be null");

        logger.debug("Start searching user by refresh token: {}", tokenRefresh);

        RefreshTokenModel refreshToken = jwtRefreshTokenRepository.findByTokenRefresh(tokenRefresh)
                .orElseThrow(() -> new TokenException("Token not found", null));

        return refreshToken.getUser();
    }


    public TokenBearerResponse getNewAccessToken(TokenBearer tokenRefresh) throws TokenException {
        Objects.requireNonNull(tokenRefresh, "Token cannot be null");

        logger.debug("Start searching and check refresh tokenRefresh");
        RefreshTokenModel refreshToken = findTokenService.findRefreshToken(tokenRefresh);

        if(refreshToken.getStatus() == TokenStatus.ACTIVE) {
            validTokenService.lowLevelVerifyToken(tokenRefresh);
        }

        UserModel user = getUserByRefreshToken(tokenRefresh.getToken());

        logger.debug("Gen new access token");
        return genTokenService.generateAccessJwtToken(new SecurityUser(user));
    }

}
