package com.ukhanov.RealHelpDesk.core.security.auth.tokens.service;

import com.ukhanov.RealHelpDesk.core.security.auth.tokens.dto.RefreshTokenRequest;
import com.ukhanov.RealHelpDesk.core.security.auth.tokens.dto.TokenBearerResponse;
import com.ukhanov.RealHelpDesk.core.security.auth.tokens.exception.TokenException;
import com.ukhanov.RealHelpDesk.core.security.auth.tokens.model.TokenBearer;
import com.ukhanov.RealHelpDesk.core.security.auth.tokens.model.TokenBearerImpl;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenService {
    private final GetTokenService getTokenService;

    public RefreshTokenService(GetTokenService getTokenService) {
        this.getTokenService = getTokenService;
    }

    public TokenBearerResponse refreshAccessToken(RefreshTokenRequest refreshTokenRequest) throws TokenException {
        TokenBearer tokenBearer = new TokenBearerImpl(refreshTokenRequest.getRefreshToken());
        return getTokenService.getNewAccessToken(tokenBearer);
    }
}
