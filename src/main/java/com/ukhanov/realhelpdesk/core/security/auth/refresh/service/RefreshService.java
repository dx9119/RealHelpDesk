package com.ukhanov.realhelpdesk.core.security.auth.refresh.service;

import com.ukhanov.realhelpdesk.core.security.auth.tokens.dto.TokenStatusResponse;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.exception.TokenException;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.RefreshTokenModel;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.Token;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.TokenBearer;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.TokenStatus;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.*;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.utils.JwtClaims;
import com.ukhanov.realhelpdesk.core.security.auth.refresh.exception.RefreshException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RefreshService {
    private final DecodeTokenService decodeTokenService;
    private final GetTokenService getTokenService;
    private final ValidTokenService validTokenService;
    private final FindTokenService findTokenService;
    private final SaveTokenService saveTokenService;


    private static final Logger logger = LoggerFactory.getLogger(RefreshService.class);

    public RefreshService(DecodeTokenService decodeTokenService, GetTokenService getTokenService, ValidTokenService validTokenService, FindTokenService findTokenService, SaveTokenService saveTokenService) {
        this.decodeTokenService = decodeTokenService;
        this.getTokenService = getTokenService;
        this.validTokenService = validTokenService;
        this.findTokenService = findTokenService;
        this.saveTokenService = saveTokenService;
    }

    public String updateAccess (HttpServletRequest request) throws TokenException, RefreshException {
        Token refreshToken = new Token(
                decodeTokenService.extractTokenFromCookies(request,"refreshToken")
        );

        TokenStatusResponse tokenStatus = getTokenService.getStatusRefreshTokenFromCookie(request);
        if(tokenStatus.getTokenStatus() != TokenStatus.ACTIVE){
            throw new RefreshException("Токен обновления не активен");
        }
        try {
            validTokenService.lowLevelVerifyToken(refreshToken, JwtClaims.TYPE_REFRESH);
            TokenBearer newAccessToken = getTokenService.getNewAccessToken(refreshToken);
            return newAccessToken.getToken();
        }
        catch (TokenException | JwtException e){
            revokeQuietly(refreshToken);
            logger.warn("Refresh-токен отклонён: {}", e.getClass().getSimpleName());
            throw new RefreshException("Токен обновления недействителен");
        }
    }

    // Помечаем токен как отозванный; ошибку поиска не пробрасываем — ответ уже сформирован
    private void revokeQuietly(Token refreshToken) {
        try {
            RefreshTokenModel token = findTokenService.findRefreshToken(refreshToken);
            token.setStatus(TokenStatus.REVOKED);
            saveTokenService.saveRefreshToken(token);
        } catch (TokenException e) {
            logger.debug("Не удалось пометить refresh-токен как REVOKED");
        }
    }

}
