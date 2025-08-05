package com.ukhanov.realhelpdesk.core.security.auth.refresh.service;

import com.ukhanov.realhelpdesk.core.security.auth.logout.service.LogoutService;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.dto.TokenStatusResponse;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.exception.TokenException;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.RefreshTokenModel;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.Token;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.TokenBearer;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.TokenStatus;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.*;
import com.ukhanov.realhelpdesk.core.security.auth.refresh.exception.RefreshException;
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
            throw new RefreshException("Refresh token not active");
        }
        try {
            validTokenService.lowLevelVerifyToken(refreshToken);
            TokenBearer newAccessToken = getTokenService.getNewAccessToken(refreshToken);
            logger.debug("new access token send");
            return newAccessToken.getToken();
        }
        catch (TokenException e){
            RefreshTokenModel token = findTokenService.findRefreshToken(refreshToken);
            token.setStatus(TokenStatus.REVOKED);
            saveTokenService.saveRefreshToken(token);
            throw new RefreshException("Refresh token is dead");
        }
    }

}
