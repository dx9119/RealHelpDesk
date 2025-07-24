package com.ukhanov.realhelpdesk.core.security.auth.register.service;

import com.ukhanov.realhelpdesk.core.mail.dto.EmailConfirmationDto;
import com.ukhanov.realhelpdesk.core.mail.exception.EmailAccessDeniedException;
import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;
import com.ukhanov.realhelpdesk.core.mail.service.EmailDeliveryService;
import com.ukhanov.realhelpdesk.core.security.auth.mapper.AuthMapper;
import com.ukhanov.realhelpdesk.core.security.auth.register.dto.RegisterRequest;
import com.ukhanov.realhelpdesk.core.security.auth.register.exception.RegistrationException;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.dto.TokensResponse;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.GetTokenService;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.core.security.user.service.UserDomainService;
import jakarta.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class RegistrationService {
    private static final Logger logger = LoggerFactory.getLogger(RegistrationService.class);

    private final PasswordEncoder passwordEncoder;
    private final UserDomainService userDomainService;
    private final GetTokenService getTokenService;
    private final EmailDeliveryService emailDeliveryService;


    public RegistrationService(PasswordEncoder passwordEncoder,
                               UserDomainService userDomainService,
                               GetTokenService getTokenService, EmailDeliveryService emailDeliveryService) {
        this.passwordEncoder = passwordEncoder;
        this.userDomainService = userDomainService;
        this.getTokenService = getTokenService;
        this.emailDeliveryService = emailDeliveryService;
    }

    public UserModel addUser(RegisterRequest registerRequest)
        throws RegistrationException, MessagingException, EmailAccessDeniedException {
        Objects.requireNonNull(registerRequest, "getTokensRequest cannot be null");
        logger.info("Registration start for email: {}", registerRequest.getEmail());

        // Проверяем наличие прошлой регистрации
        if (userDomainService.isUserExistsByEmail(registerRequest.getEmail())) {
            throw new RegistrationException("Почта уже используется.",
                    new Throwable("Пользователь может занимать только один аккаунт."));
        }

        // Проверяем длину пароля(перестраховка)
        if (registerRequest.getPassword().length() < 8) {
            throw new RegistrationException("Минимальная длина пароля - 8 символов.", new Throwable("Слишком короткий пароль."));
        }

        // создаем пользователя
        UserModel newUser = AuthMapper.toEntity(
                registerRequest,
                passwordEncoder.encode(registerRequest.getPassword())
        );

        // Сохраняем пользователя
        userDomainService.saveUser(newUser);
        logger.info("User registered successfully, email: {}", registerRequest.getEmail());

        // Оповещаем админа о регистрации
        emailDeliveryService.sendAdminNotification(
            "New registration:"+newUser.getEmail(),
            "info:"+newUser.toString(),
            NotificationEvent.NEW_SYSTEM_MESSAGE);

        // Отправляем письмо с подтверждением
        EmailConfirmationDto dto = EmailConfirmationDto.builder()
            .token(newUser.getVerifyEmailToken())
            .build();

        emailDeliveryService.sendUserNotification(
            newUser.getEmail(),
            dto.getSubject(),
            dto.getMessage(),
            NotificationEvent.NEW_SYSTEM_MESSAGE
        );

        return newUser;
    }

    public TokensResponse processRegistration(RegisterRequest registerRequest)
        throws RegistrationException, MessagingException, EmailAccessDeniedException {
        UserModel user = addUser(registerRequest);
        return getTokenService.getNewTokens(user);
    }

}






