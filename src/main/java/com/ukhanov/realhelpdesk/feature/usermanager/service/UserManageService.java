package com.ukhanov.realhelpdesk.feature.usermanager.service;

import com.ukhanov.realhelpdesk.core.mail.model.EmailTemplates;
import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;
import com.ukhanov.realhelpdesk.core.mail.service.EmailDeliveryService;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.exception.TokenException;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.RefreshTokenModel;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.TokenStatus;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.GetTokenService;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.SaveTokenService;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.SetTokenService;
import com.ukhanov.realhelpdesk.feature.usermanager.dto.RecoveryRequest;
import com.ukhanov.realhelpdesk.feature.usermanager.dto.NewPasswdRequest;
import com.ukhanov.realhelpdesk.core.security.user.CurrentUserProvider;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.core.security.user.service.UserDomainService;
import com.ukhanov.realhelpdesk.feature.usermanager.dto.UserInfoRequest;
import com.ukhanov.realhelpdesk.feature.usermanager.dto.UserInfoResponse;
import com.ukhanov.realhelpdesk.feature.usermanager.mapper.UserMapper;

import java.io.UnsupportedEncodingException;
import java.util.Objects;
import java.util.UUID;

import jakarta.mail.MessagingException;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserManageService {

  private static final Logger logger = LoggerFactory.getLogger(UserManageService.class);

  private final CurrentUserProvider currentUserProvider;
  private final UserDomainService userDomainService;
  private final EmailDeliveryService emailDeliveryService;
  private final PasswordEncoder passwordEncoder;
  private final GetTokenService getTokenService;
  private final SaveTokenService saveTokenService;
  private final SetTokenService setTokenService;


  public UserManageService(CurrentUserProvider currentUserProvider,
                           UserDomainService userDomainService,
                           EmailDeliveryService emailDeliveryService,
                           PasswordEncoder passwordEncoder,
                           GetTokenService getTokenService, SaveTokenService saveTokenService, SetTokenService setTokenService) {
    this.currentUserProvider = currentUserProvider;
    this.userDomainService = userDomainService;
      this.emailDeliveryService = emailDeliveryService;
      this.passwordEncoder = passwordEncoder;
      this.getTokenService = getTokenService;
      this.saveTokenService = saveTokenService;
      this.setTokenService = setTokenService;
  }

  public UserInfoResponse getUserInfo() {
    UserModel user = currentUserProvider.getCurrentUserModel();
    return UserMapper.toResponse(user);
  }


  @Transactional
  public UserInfoResponse updateUserInfo(UserInfoRequest request) {
    Objects.requireNonNull(request, "Request не должен быть null");

    logger.debug("Запрос на обновление информации пользователя. Имя: {}, Фамилия: {}, Отчество: {}, Доп. информация: {}",
            request.getFirstName(), request.getLastName(), request.getMiddleName(), request.getAdditionalInfo());

    UserModel currentUser = currentUserProvider.getCurrentUserModel();

    currentUser.setFirstName(request.getFirstName());
    currentUser.setLastName(request.getLastName());
    currentUser.setMiddleName(request.getMiddleName());
    currentUser.setAdditionalInfo(request.getAdditionalInfo());

    UserModel updatedUser = userDomainService.saveUser(currentUser);

    return UserMapper.toResponse(updatedUser);
  }


  public void sendResetLink(RecoveryRequest request) throws MessagingException, UnsupportedEncodingException {
    Objects.requireNonNull(request, "RecoveryRequest не должен быть null");

    logger.debug("Запрос на сброс пароля. Email: {}", request.getEmail());

    UserModel user = userDomainService.getUserByEmail(request.getEmail());

    UUID recoverPasswdToken = UUID.randomUUID();
    user.setRecoveryPasswdToken(recoverPasswdToken);

    userDomainService.saveUser(user);

    emailDeliveryService.sendEmail(
            user.getEmail(),
            EmailTemplates.passwordResetSubject(),
            EmailTemplates.passwordResetBody(recoverPasswdToken.toString()),
            NotificationEvent.RECOVERY_PASSWORD
    );
  }


  public void setNewPasswd(UUID code, NewPasswdRequest request) throws TokenException {
    Objects.requireNonNull(code, "Код не должен быть null");
    Objects.requireNonNull(request, "Запрос не должен быть null");

    logger.debug("Запрос на смену пароля по коду восстановления: {}", code);

    UserModel user = userDomainService.getUserByRecoveryPasswdToken(code);

    setTokenService.addNewRefreshToken(user); // нужен хотя бы один активный refresh токен для логина

    user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
    user.setRecoveryPasswdToken(UUID.randomUUID());

    RefreshTokenModel token = getTokenService.getActiveRefreshToken(user);
    token.setStatus(TokenStatus.PASSWD_CHANGE);

    userDomainService.saveUser(user);
    saveTokenService.saveRefreshToken(token);
  }


}
