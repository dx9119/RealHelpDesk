package com.ukhanov.realhelpdesk.core.security.user;

import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.core.security.user.service.UserDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

// Провайдер текущего пользователя
@Component
public class CurrentUserProvider {

    private static final Logger logger = LoggerFactory.getLogger(CurrentUserProvider.class);

    private final UserDomainService userDomainService;

    public CurrentUserProvider(UserDomainService userDomainService) {
        this.userDomainService = userDomainService;
    }

    // Получить ID текущего пользователя
    public UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Objects.requireNonNull(authentication, "Пользователь не авторизован");

        String rawId = authentication.getName();

        try {
            return UUID.fromString(rawId);
        } catch (IllegalArgumentException e) {
            logger.error("Неверный формат UUID: {}", rawId);
            throw new IllegalStateException("Недопустимый идентификатор пользователя");
        }
    }

    // Получить модель текущего пользователя
    public UserModel getCurrentUserModel() {
        UUID userId = getCurrentUserId();
        return userDomainService.getUserById(userId);
    }
}
