package com.ukhanov.RealHelpDesk.core.security.auth.login.service;

import com.ukhanov.RealHelpDesk.core.security.auth.login.dto.LoginRequest;
import com.ukhanov.RealHelpDesk.core.security.auth.tokens.dto.TokensResponse;
import com.ukhanov.RealHelpDesk.core.security.auth.tokens.exception.TokenException;
import com.ukhanov.RealHelpDesk.core.security.auth.tokens.service.GetTokenService;
import com.ukhanov.RealHelpDesk.core.security.user.SecurityUser;
import com.ukhanov.RealHelpDesk.core.security.user.service.CustomUserDetailsService;
import com.ukhanov.RealHelpDesk.core.security.user.service.UserDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class LoginService {
    private static final Logger logger = LoggerFactory.getLogger(LoginService.class);

    private final PasswordEncoder passwordEncoder;
    private final GetTokenService getTokenService;
    private final UserDomainService userDomainService;
    private final CustomUserDetailsService customUserDetailsService;

    public LoginService(PasswordEncoder passwordEncoder, GetTokenService getTokenService, UserDomainService userDomainService, CustomUserDetailsService customUserDetailsService) {
        this.passwordEncoder = passwordEncoder;
        this.getTokenService = getTokenService;

        this.userDomainService = userDomainService;
        this.customUserDetailsService = customUserDetailsService;
    }

    // Проверяем, если пользователь существует и пароль правильный - отдаем токены
    // Аутентификация/Аутентификация происходит в фильтре JwtAuthFilter
    public TokensResponse processLogin(LoginRequest loginRequest) throws TokenException {
        if(!userDomainService.isUserExistsByEmail(loginRequest.getEmail())){
            logger.info("Login failed. User {} not found", loginRequest.getEmail());
            throw new UsernameNotFoundException("User not found");
        }

        SecurityUser user = customUserDetailsService.loadUserByUsername(loginRequest.getEmail());
        if(!isPasswordValid(loginRequest.getPassword(), user.getPassword()))
        {
            logger.info("Login failed. Invalid password for user {}", loginRequest.getEmail());
            throw new BadCredentialsException("Invalid password");
        }

        return getTokenService.getActiveTokens(user.getOriginalUser());
    }

    public boolean isPasswordValid(String password, String encodedPassword) {
        return passwordEncoder.matches(password, encodedPassword);
    }

}
