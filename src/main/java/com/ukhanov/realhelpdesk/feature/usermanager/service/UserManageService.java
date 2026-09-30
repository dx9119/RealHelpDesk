package com.ukhanov.realhelpdesk.feature.usermanager.service;

import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.util.Objects;

import jakarta.mail.MessagingException;
import jakarta.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.ukhanov.realhelpdesk.core.mail.model.EmailTemplates;
import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;
import com.ukhanov.realhelpdesk.core.mail.service.EmailDeliveryService;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.exception.TokenException;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.RefreshTokenModel;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.TokenStatus;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.GetTokenService;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.SaveTokenService;
import com.ukhanov.realhelpdesk.core.security.user.CurrentUserProvider;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.core.security.user.service.UserDomainService;
import com.ukhanov.realhelpdesk.feature.usermanager.dto.NewPasswdRequest;
import com.ukhanov.realhelpdesk.feature.usermanager.dto.RecoveryRequest;
import com.ukhanov.realhelpdesk.feature.usermanager.dto.UserInfoRequest;
import com.ukhanov.realhelpdesk.feature.usermanager.dto.UserInfoResponse;
import com.ukhanov.realhelpdesk.feature.usermanager.mapper.UserMapper;

@Service
public class UserManageService {

    private static final Logger logger = LoggerFactory.getLogger(UserManageService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CurrentUserProvider currentUserProvider;
    private final UserDomainService userDomainService;
    private final EmailDeliveryService emailDeliveryService;
    private final PasswordEncoder passwordEncoder;
    private final GetTokenService getTokenService;
    private final SaveTokenService saveTokenService;
    private final EmailTemplates emailTemplates;

    public UserManageService(CurrentUserProvider currentUserProvider, UserDomainService userDomainService,
            EmailDeliveryService emailDeliveryService, PasswordEncoder passwordEncoder, GetTokenService getTokenService,
            SaveTokenService saveTokenService, EmailTemplates emailTemplates) {
        this.currentUserProvider = currentUserProvider;
        this.userDomainService = userDomainService;
        this.emailDeliveryService = emailDeliveryService;
        this.passwordEncoder = passwordEncoder;
        this.getTokenService = getTokenService;
        this.saveTokenService = saveTokenService;
        this.emailTemplates = emailTemplates;
    }

    public UserInfoResponse getUserInfo() {
        UserModel user = currentUserProvider.getCurrentUserModel();
        return UserMapper.toResponse(user);
    }

    @Transactional
    public UserInfoResponse updateUserInfo(UserInfoRequest request) {
        Objects.requireNonNull(request, "Request не должен быть null");

        UserModel currentUser = currentUserProvider.getCurrentUserModel();

        currentUser.setFirstName(request.getFirstName());
        currentUser.setLastName(request.getLastName());
        currentUser.setMiddleName(request.getMiddleName());
        currentUser.setAdditionalInfo(request.getAdditionalInfo());

        UserModel updatedUser = userDomainService.saveUser(currentUser);
        logger.info("Профиль пользователя {} обновлён", updatedUser.getId());

        return UserMapper.toResponse(updatedUser);
    }

    public void sendResetLink(RecoveryRequest request) throws MessagingException, UnsupportedEncodingException {
        Objects.requireNonNull(request, "RecoveryRequest не должен быть null");

        logger.debug("Запрошено восстановление пароля, email={}", request.getEmail());

        UserModel user = userDomainService.getUserByEmail(request.getEmail());

        Long recoverPasswdToken = RANDOM.nextLong();
        user.setRecoveryPasswdToken(recoverPasswdToken);

        userDomainService.saveUser(user);

        emailDeliveryService.sendEmail(user.getEmail(), emailTemplates.passwordResetSubject(),
                emailTemplates.passwordResetBody(recoverPasswdToken.toString()), NotificationEvent.RECOVERY_PASSWORD);
        logger.info("Отправлено письмо восстановления пароля, userId={}", user.getId());
    }

    @Transactional
    public void setNewPasswd(Long code, NewPasswdRequest request) throws TokenException {
        Objects.requireNonNull(code, "Код не должен быть null");
        Objects.requireNonNull(request, "Запрос не должен быть null");

        UserModel user = userDomainService.getUserByRecoveryPasswdToken(code);

        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRecoveryPasswdToken(RANDOM.nextLong());
        // Отзываем access-токены, выданные до смены пароля
        user.incrementTokenVersion();

        userDomainService.saveUser(user);

        // Отзываем все ранее выданные refresh-токены; новый выдастся при следующем входе
        int revoked = 0;
        for (RefreshTokenModel token : getTokenService.getActiveRefreshTokens(user)) {
            token.setStatus(TokenStatus.PASSWD_CHANGE);
            saveTokenService.saveRefreshToken(token);
            revoked++;
        }
        logger.info("Пароль изменён, отозвано refresh-токенов: {}, userId={}", revoked, user.getId());
    }

}
