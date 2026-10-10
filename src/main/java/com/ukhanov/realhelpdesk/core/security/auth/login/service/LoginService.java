package com.ukhanov.realhelpdesk.core.security.auth.login.service;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.ukhanov.realhelpdesk.core.security.auth.login.dto.LoginRequest;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.dto.TokensResponse;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.exception.TokenException;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.GetTokenService;
import com.ukhanov.realhelpdesk.core.security.user.SecurityUser;
import com.ukhanov.realhelpdesk.core.security.user.model.UserStatus;
import com.ukhanov.realhelpdesk.core.security.user.service.CustomUserDetailsService;
import com.ukhanov.realhelpdesk.core.security.user.service.UserDomainService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class LoginService {

    private final PasswordEncoder passwordEncoder;
    private final GetTokenService getTokenService;
    private final UserDomainService userDomainService;
    private final CustomUserDetailsService customUserDetailsService;
    // Проверяем, если пользователь существует и пароль правильный - отдаем токены
    // Аутентификация/Аутентификация происходит в фильтре JwtAuthFilter
    public TokensResponse processLogin(LoginRequest loginRequest) throws TokenException {
        if (!userDomainService.isUserExistsByEmail(loginRequest.email())) {
            logger.warn("Отказ во входе: пользователь не найден, email={}", loginRequest.email());
            throw new UsernameNotFoundException("Ошибка авторизации: неверный логин, пароль или отсутствующий пользователь");
        }

        SecurityUser user = customUserDetailsService.loadUserByUsername(loginRequest.email());

        if (user.getOriginalUser().getUserStatus() != UserStatus.ACTIVE) {
            logger.warn("Отказ во входе: учётная запись не активна, email={}", loginRequest.email());
            throw new BadCredentialsException("Ошибка авторизации: неверный логин, пароль или отсутствующий пользователь");
        }

        if (!isPasswordValid(loginRequest.password(), user.getPassword())) {
            logger.warn("Отказ во входе: неверный пароль, email={}", loginRequest.email());
            throw new BadCredentialsException("Ошибка авторизации: неверный логин, пароль или отсутствующий пользователь");

        }

        // Ротация: при каждом входе выдаём свежий refresh-токен (в БД хранится только его хеш)
        return getTokenService.getNewTokens(user.getOriginalUser());
    }

    public boolean isPasswordValid(String password, String encodedPassword) {
        return passwordEncoder.matches(password, encodedPassword);
    }

}
