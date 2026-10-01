package com.ukhanov.realhelpdesk.core.security.auth.tokens.service;

import java.util.List;
import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ukhanov.realhelpdesk.core.security.auth.tokens.dto.TokenBearerResponse;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.dto.TokenStatusResponse;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.dto.TokensResponse;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.exception.TokenException;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.RefreshTokenModel;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.Token;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.TokenBearer;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.TokenStatus;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.repository.JwtRefreshTokenRepository;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.utils.JwtClaims;
import com.ukhanov.realhelpdesk.core.security.user.SecurityUser;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;

@Service
public class GetTokenService {
    private static final Logger logger = LoggerFactory.getLogger(GetTokenService.class);

    private final GenTokenService genTokenService;
    private final SaveTokenService saveTokenService;
    private final JwtRefreshTokenRepository jwtRefreshTokenRepository;
    private final FindTokenService findTokenService;
    private final ValidTokenService validTokenService;
    private final DecodeTokenService decodeTokenService;

    public GetTokenService(GenTokenService genTokenService, SaveTokenService saveTokenService,
            JwtRefreshTokenRepository jwtRefreshTokenRepository, FindTokenService findTokenService, ValidTokenService validTokenService,
            DecodeTokenService decodeTokenService) {
        this.genTokenService = genTokenService;
        this.saveTokenService = saveTokenService;
        this.jwtRefreshTokenRepository = jwtRefreshTokenRepository;
        this.findTokenService = findTokenService;
        this.validTokenService = validTokenService;
        this.decodeTokenService = decodeTokenService;
    }

    // Новые токены: access + свежий refresh. Ротация при каждом входе/регистрации:
    // предыдущие активные refresh-токены пользователя отзываются, поэтому старый
    // токен перестаёт быть валидным сразу после выдачи нового.
    @Transactional
    public TokensResponse getNewTokens(UserModel user) {
        SecurityUser securityUser = new SecurityUser(user);

        TokenBearerResponse tokenBearerResponse = genTokenService.generateAccessJwtToken(securityUser);
        RefreshTokenModel refreshToken = genTokenService.generateRefreshJwtToken(securityUser);

        int revoked = revokeActiveRefreshTokens(user);
        if (revoked > 0) {
            logger.info("Ротация refresh-токенов: отозвано {}, userId={}", revoked, user.getId());
        }
        saveTokenService.saveRefreshToken(refreshToken);

        return TokensResponse.builder().accessToken(tokenBearerResponse.getToken()).refreshToken(refreshToken.getRawToken())
                .message(securityUser.getUsername()).build();
    }

    private int revokeActiveRefreshTokens(UserModel user) {
        List<RefreshTokenModel> active = jwtRefreshTokenRepository.findAllByUserEmailAndStatus(user.getEmail(), TokenStatus.ACTIVE);
        for (RefreshTokenModel token : active) {
            token.setStatus(TokenStatus.REVOKED);
            saveTokenService.saveRefreshToken(token);
        }
        return active.size();
    }

    // Все активные refresh-токены пользователя (для отзыва, например при смене пароля)
    public List<RefreshTokenModel> getActiveRefreshTokens(UserModel user) {
        Objects.requireNonNull(user, "Пользователь не может быть null!");

        return jwtRefreshTokenRepository.findAllByUserEmailAndStatus(user.getEmail(), TokenStatus.ACTIVE);
    }

    private Token extractAndValidateToken(HttpServletRequest request, String tokenName) throws TokenException {
        Objects.requireNonNull(request, "Запрос не может быть null");

        Token token = new Token(decodeTokenService.extractTokenFromCookies(request, tokenName));
        if (token.getToken() == null || token.getToken().isEmpty()) {
            throw new TokenException("Cookie с именем " + tokenName + " не найдена");
        }

        return token;
    }

    public TokenStatusResponse getStatusRefreshTokenFromCookie(HttpServletRequest request) throws TokenException {
        Token tokenRefresh = extractAndValidateToken(request, "refreshToken");
        RefreshTokenModel refreshToken = findTokenService.findRefreshToken(tokenRefresh);

        return TokenStatusResponse.builder().tokenStatus(refreshToken.getStatus()).createdAt(refreshToken.getCreatedAt()).build();
    }

    // Новый access по валидному активному refresh-токену
    public TokenBearerResponse getNewAccessToken(TokenBearer tokenRefresh) throws TokenException {
        Objects.requireNonNull(tokenRefresh, "Токен не может быть null");

        RefreshTokenModel refreshToken = findTokenService.findRefreshToken(tokenRefresh);

        if (refreshToken.getStatus() != TokenStatus.ACTIVE) {
            throw new TokenException("Токен обновления не активен", null);
        }

        validTokenService.lowLevelVerifyToken(tokenRefresh, JwtClaims.TYPE_REFRESH);

        UserModel user = refreshToken.getUser();
        return genTokenService.generateAccessJwtToken(new SecurityUser(user));
    }
}
