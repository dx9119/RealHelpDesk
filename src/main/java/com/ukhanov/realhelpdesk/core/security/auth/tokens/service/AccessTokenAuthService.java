package com.ukhanov.realhelpdesk.core.security.auth.tokens.service;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ukhanov.realhelpdesk.core.security.auth.tokens.exception.TokenException;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.utils.JwtClaims;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.core.security.user.model.UserStatus;
import com.ukhanov.realhelpdesk.core.security.user.repository.UserRepository;

import io.jsonwebtoken.Claims;

// Проверка access-токена против состояния пользователя в БД:
// статус аккаунта и версия токена (отзыв после логаута/смены пароля)
@Service
public class AccessTokenAuthService {

    private static final Logger logger = LoggerFactory.getLogger(AccessTokenAuthService.class);

    private final UserRepository userRepository;

    public AccessTokenAuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserModel loadVerifiedUser(Claims claims) throws TokenException {
        Objects.requireNonNull(claims, "Claims не могут быть null");

        Long userId = parseUserId(claims.getSubject());

        UserModel user = userRepository.findById(userId).orElseThrow(() -> new TokenException("Пользователь из токена не найден", null));

        if (user.getUserStatus() != UserStatus.ACTIVE) {
            logger.debug("Токен отклонён: пользователь {} не активен", userId);
            throw new TokenException("Учётная запись неактивна", null);
        }

        Integer tokenVersion = claims.get(JwtClaims.TOKEN_VERSION, Integer.class);
        if (tokenVersion == null || tokenVersion.intValue() != user.getTokenVersion()) {
            logger.debug("Токен отклонён: устаревшая версия токена пользователя {}", userId);
            throw new TokenException("Токен отозван", null);
        }

        return user;
    }

    private Long parseUserId(String subject) throws TokenException {
        if (subject == null || subject.isBlank()) {
            throw new TokenException("Отсутствует subject токена", null);
        }
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException e) {
            throw new TokenException("Некорректный subject токена", null);
        }
    }
}
