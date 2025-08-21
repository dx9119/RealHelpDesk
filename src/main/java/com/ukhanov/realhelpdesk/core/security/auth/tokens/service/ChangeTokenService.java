package com.ukhanov.realhelpdesk.core.security.auth.tokens.service;

import com.ukhanov.realhelpdesk.core.security.auth.tokens.exception.TokenException;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.RefreshTokenModel;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.Token;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.TokenBearer;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.TokenStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class ChangeTokenService {
    private static final Logger logger = LoggerFactory.getLogger(ChangeTokenService.class);

    private final SaveTokenService saveTokenService;
    private final DecodeTokenService decodeTokenService;
    private final FindTokenService findTokenService;

    public ChangeTokenService(SaveTokenService saveTokenService, GetTokenService getTokenService,
        DecodeTokenService decodeTokenService, FindTokenService findTokenService) {
        this.saveTokenService = saveTokenService;
      this.decodeTokenService = decodeTokenService;
      this.findTokenService = findTokenService;
    }

    public RefreshTokenModel changeStatusRefreshToken(
        HttpServletRequest request,
        TokenStatus newStatus )
        throws TokenException {
        logger.debug("Начало изменения статуса токена");

        TokenBearer tokenBearer =
            new Token(
                decodeTokenService
                    .extractTokenFromCookies(request, "refreshToken")
            );
        logger.debug("Получен токен из cookies: {}", tokenBearer);

        RefreshTokenModel RefreshToken = findTokenService.findRefreshToken(tokenBearer);
        logger.debug("Поиск токена: завершён");

        RefreshToken.setStatus(newStatus);
        logger.debug("Статус токена изменён на {}", newStatus);

        return saveTokenService.saveRefreshToken(RefreshToken);
    }
}
