package com.ukhanov.realhelpdesk.core.security.auth.logout.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;

import org.springframework.stereotype.Service;

import com.ukhanov.realhelpdesk.core.security.auth.logout.exception.LogoutException;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.exception.TokenException;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.TokenStatus;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.ChangeTokenService;
import com.ukhanov.realhelpdesk.core.security.user.CurrentUserProvider;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.core.security.user.service.UserDomainService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class LogoutService {

    private final CurrentUserProvider currentUserProvider;
    private final ChangeTokenService changeTokenService;
    private final UserDomainService userDomainService;

    @Transactional
    public void processLogout(HttpServletRequest request) throws LogoutException, TokenException {

        changeTokenService.changeStatusRefreshToken(request, TokenStatus.LOGOUT);

        // Отзываем все ранее выданные access-токены пользователя
        UserModel user = currentUserProvider.getCurrentUserModel();
        user.incrementTokenVersion();
        userDomainService.saveUser(user);

        logger.info("Выход выполнен, access-токены пользователя {} отозваны", user.getId());
    }

}
