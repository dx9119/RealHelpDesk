package com.ukhanov.realhelpdesk.core.security.auth.tokens.service;

import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.RefreshTokenModel;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.TokenStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class ChangeTokenService {
    private static final Logger logger = LoggerFactory.getLogger(ChangeTokenService.class);

    private final SaveTokenService saveTokenService;

    public ChangeTokenService(SaveTokenService saveTokenService, GetTokenService getTokenService) {
        this.saveTokenService = saveTokenService;
    }

    public RefreshTokenModel changeStatusRefreshToken(RefreshTokenModel token, TokenStatus newStatus) {
        Objects.requireNonNull(token, "token must not be null");
        Objects.requireNonNull(newStatus, "status must not be null");

        token.setStatus(newStatus);
        logger.debug("Changing status token {} to {}", token, newStatus);

        return saveTokenService.saveRefreshToken(token);
    }

}
