package com.ukhanov.realhelpdesk.core.security.user;

import java.util.Objects;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.core.security.user.service.UserDomainService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Провайдер текущего пользователя
@Slf4j
@RequiredArgsConstructor
@Component
public class CurrentUserProvider {

    private final UserDomainService userDomainService;
    // Получить ID текущего пользователя
    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Objects.requireNonNull(authentication, "Пользователь не авторизован");

        String rawId = authentication.getName();

        try {
            return Long.valueOf(rawId);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Недопустимый идентификатор пользователя");
        }
    }

    // Получить модель текущего пользователя
    public UserModel getCurrentUserModel() {
        Long userId = getCurrentUserId();
        return userDomainService.getUserById(userId);
    }
}
