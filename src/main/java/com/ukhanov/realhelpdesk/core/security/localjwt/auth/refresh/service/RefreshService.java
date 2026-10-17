package com.ukhanov.realhelpdesk.core.security.localjwt.auth.refresh.service;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Service;

import com.ukhanov.realhelpdesk.core.observability.AuthMetrics;
import com.ukhanov.realhelpdesk.core.security.localjwt.auth.refresh.exception.RefreshException;
import com.ukhanov.realhelpdesk.core.security.localjwt.auth.tokens.dto.TokenStatusResponse;
import com.ukhanov.realhelpdesk.core.security.localjwt.auth.tokens.exception.TokenException;
import com.ukhanov.realhelpdesk.core.security.localjwt.auth.tokens.model.RefreshTokenModel;
import com.ukhanov.realhelpdesk.core.security.localjwt.auth.tokens.model.Token;
import com.ukhanov.realhelpdesk.core.security.localjwt.auth.tokens.model.TokenBearer;
import com.ukhanov.realhelpdesk.core.security.localjwt.auth.tokens.model.TokenStatus;
import com.ukhanov.realhelpdesk.core.security.localjwt.auth.tokens.service.DecodeTokenService;
import com.ukhanov.realhelpdesk.core.security.localjwt.auth.tokens.service.FindTokenService;
import com.ukhanov.realhelpdesk.core.security.localjwt.auth.tokens.service.GetTokenService;
import com.ukhanov.realhelpdesk.core.security.localjwt.auth.tokens.service.SaveTokenService;
import com.ukhanov.realhelpdesk.core.security.localjwt.auth.tokens.service.ValidTokenService;
import com.ukhanov.realhelpdesk.core.security.localjwt.auth.tokens.utils.JwtClaims;

import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class RefreshService {
    private final DecodeTokenService decodeTokenService;
    private final GetTokenService getTokenService;
    private final ValidTokenService validTokenService;
    private final FindTokenService findTokenService;
    private final SaveTokenService saveTokenService;
    private final AuthMetrics authMetrics;

    public String updateAccess(HttpServletRequest request) throws TokenException, RefreshException {
        Token refreshToken = new Token(decodeTokenService.extractTokenFromCookies(request, "refreshToken"));

        TokenStatusResponse tokenStatus = getTokenService.getStatusRefreshTokenFromCookie(request);
        if (tokenStatus.tokenStatus() != TokenStatus.ACTIVE) {
            authMetrics.record(AuthMetrics.TYPE_REFRESH, AuthMetrics.RESULT_FAILURE);
            throw new RefreshException("Токен обновления не активен");
        }
        try {
            validTokenService.lowLevelVerifyToken(refreshToken, JwtClaims.TYPE_REFRESH);
            // Событие «access-токен обновлён» с userId пишет GetTokenService.getNewAccessToken:
            // повторять здесь строку без идентификатора смысла нет
            TokenBearer newAccessToken = getTokenService.getNewAccessToken(refreshToken);
            authMetrics.record(AuthMetrics.TYPE_REFRESH, AuthMetrics.RESULT_SUCCESS);
            return newAccessToken.getToken();
        } catch (TokenException | JwtException e) {
            revokeQuietly(refreshToken);
            authMetrics.record(AuthMetrics.TYPE_REFRESH, AuthMetrics.RESULT_FAILURE);
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
            logger.warn("Не удалось отозвать refresh-токен после отклонения");
        }
    }

}
