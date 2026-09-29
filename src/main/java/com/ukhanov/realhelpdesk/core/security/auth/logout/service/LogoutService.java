package com.ukhanov.realhelpdesk.core.security.auth.logout.service;

import com.ukhanov.realhelpdesk.core.security.auth.logout.exception.LogoutException;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.exception.TokenException;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.TokenStatus;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.ChangeTokenService;
import com.ukhanov.realhelpdesk.core.security.user.CurrentUserProvider;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.core.security.user.service.UserDomainService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LogoutService {
  private static final Logger logger = LoggerFactory.getLogger(LogoutService.class);

  private final CurrentUserProvider currentUserProvider;
  private final ChangeTokenService changeTokenService;
  private final UserDomainService userDomainService;

  public LogoutService(CurrentUserProvider currentUserProvider, ChangeTokenService changeTokenService,
      UserDomainService userDomainService) {
    this.currentUserProvider = currentUserProvider;
    this.changeTokenService = changeTokenService;
    this.userDomainService = userDomainService;
  }

  @Transactional
  public void processLogout(HttpServletRequest request) throws LogoutException, TokenException {

    changeTokenService.changeStatusRefreshToken(request, TokenStatus.LOGOUT);

    // Отзываем все ранее выданные access-токены пользователя
    UserModel user = currentUserProvider.getCurrentUserModel();
    user.incrementTokenVersion();
    userDomainService.saveUser(user);

    logger.debug("Выход выполнен, access-токены пользователя {} отозваны", user.getId());
  }

}
