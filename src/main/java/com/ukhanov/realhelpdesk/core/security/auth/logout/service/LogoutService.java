package com.ukhanov.realhelpdesk.core.security.auth.logout.service;

import com.ukhanov.realhelpdesk.core.security.auth.logout.dto.LogoutResponse;
import com.ukhanov.realhelpdesk.core.security.auth.logout.exception.LogoutException;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.dto.TokenBearerRequest;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.exception.TokenException;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.RefreshTokenModel;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.TokenStatus;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.ChangeTokenService;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.GetTokenService;
import com.ukhanov.realhelpdesk.core.security.user.CurrentUserProvider;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LogoutService {
   private static final Logger logger = LoggerFactory.getLogger(LogoutService.class);

   private final CurrentUserProvider currentUserProvider;
   private final ChangeTokenService changeTokenService;
   private final GetTokenService getTokenService;

    public LogoutService(CurrentUserProvider currentUserProvider, ChangeTokenService changeTokenService, GetTokenService getTokenService) {
        this.currentUserProvider = currentUserProvider;
        this.changeTokenService = changeTokenService;
        this.getTokenService = getTokenService;
    }

    public LogoutResponse processLogout(TokenBearerRequest logoutToken) throws LogoutException, TokenException {

        UserModel currentUser = currentUserProvider.getCurrentUserModel();
        RefreshTokenModel currentToken = getTokenService.getActiveRefreshToken(currentUser);

        if(!currentToken.equalsByToken(logoutToken.getToken())){
            logger.debug(currentToken.getToken() + " != " + logoutToken.getToken());
            throw new LogoutException("Logout token is not valid");
        }

        changeTokenService.changeStatusRefreshToken(currentToken, TokenStatus.LOGOUT);

        LogoutResponse logoutResponse = new LogoutResponse();
        logoutResponse.setMessage("Logout success");
        return logoutResponse;
    }
}
