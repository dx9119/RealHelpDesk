package com.ukhanov.realhelpdesk.core.security.user;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.ukhanov.realhelpdesk.core.exception.ApiRuntimeException;
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
        if (authentication == null) {
            // Отсутствие аутентификации — штатная ситуация для защиты, а не сбой: отдаём 401
            // (advice логирует WARN), а не NPE → ERROR со стеком
            throw new ApiRuntimeException("Пользователь не авторизован", HttpStatus.UNAUTHORIZED);
        }

        String rawId = authentication.getName();

        try {
            Long userId = Long.valueOf(rawId);
            logger.debug("Текущий пользователь: id={}", userId);
            return userId;
        } catch (IllegalArgumentException e) {
            throw new ApiRuntimeException("Недопустимый идентификатор пользователя", HttpStatus.UNAUTHORIZED);
        }
    }

    // Получить модель текущего пользователя
    public UserModel getCurrentUserModel() {
        Long userId = getCurrentUserId();
        return userDomainService.getUserById(userId);
    }
}
