package com.ukhanov.realhelpdesk.core.security.user;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.core.security.user.service.UserDomainService;

// Провайдер текущего пользователя
@Component
public class CurrentUserProvider {

    private static final Logger logger = LoggerFactory.getLogger(CurrentUserProvider.class);

    private final UserDomainService userDomainService;

    public CurrentUserProvider(UserDomainService userDomainService) {
        this.userDomainService = userDomainService;
    }

    // Получить ID текущего пользователя
    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Objects.requireNonNull(authentication, "Пользователь не авторизован");

        String rawId = authentication.getName();

        try {
            return Long.valueOf(rawId);
        } catch (IllegalArgumentException e) {
            logger.error("Неверный формат ID: {}", rawId);
            throw new IllegalStateException("Недопустимый идентификатор пользователя");
        }
    }

    // Получить модель текущего пользователя
    public UserModel getCurrentUserModel() {
        Long userId = getCurrentUserId();
        return userDomainService.getUserById(userId);
    }
}
