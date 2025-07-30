package com.ukhanov.realhelpdesk.core.security.auth.logout.service;

import com.ukhanov.realhelpdesk.core.security.auth.logout.dto.LogoutResponse;
import com.ukhanov.realhelpdesk.core.security.auth.logout.exception.LogoutException;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.dto.TokenBearerRequest;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.exception.TokenException;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.RefreshTokenModel;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.Token;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.TokenBearer;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.TokenStatus;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.ChangeTokenService;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.DecodeTokenService;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.GenTokenService;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.GetTokenService;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.SaveTokenService;
import com.ukhanov.realhelpdesk.core.security.user.CurrentUserProvider;
import com.ukhanov.realhelpdesk.core.security.user.SecurityUser;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LogoutService {
  private static final Logger logger = LoggerFactory.getLogger(LogoutService.class);

  private final CurrentUserProvider currentUserProvider;
  private final ChangeTokenService changeTokenService;
  private final GenTokenService genTokenService;
  private final SaveTokenService saveTokenService;

  public LogoutService(CurrentUserProvider currentUserProvider, ChangeTokenService changeTokenService,
      GenTokenService genTokenService, SaveTokenService saveTokenService) {
    this.currentUserProvider = currentUserProvider;
    this.changeTokenService = changeTokenService;
    this.genTokenService = genTokenService;
    this.saveTokenService = saveTokenService;
  }

  public LogoutResponse processLogout(HttpServletRequest request) throws LogoutException, TokenException {

    changeTokenService.changeStatusRefreshToken(request, TokenStatus.LOGOUT);

    // Если у пользователя нет совсем рефрешь токена, то он не сможет войти в систему
    // Поэтому создаем новый
    SecurityUser user = new SecurityUser(currentUserProvider.getCurrentUserModel());
    RefreshTokenModel refreshToken = genTokenService.generateRefreshJwtToken(user);
    saveTokenService.saveRefreshToken(refreshToken);

    LogoutResponse logoutResponse = new LogoutResponse();
    logoutResponse.setMessage("Logout success");
    return logoutResponse;
  }
}
