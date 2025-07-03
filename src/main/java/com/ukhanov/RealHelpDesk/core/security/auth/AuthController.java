package com.ukhanov.RealHelpDesk.core.security.auth;

import com.ukhanov.RealHelpDesk.core.security.auth.login.dto.LoginRequest;
import com.ukhanov.RealHelpDesk.core.security.auth.login.service.LoginService;
import com.ukhanov.RealHelpDesk.core.security.auth.logout.dto.LogoutResponse;
import com.ukhanov.RealHelpDesk.core.security.auth.logout.exception.LogoutException;
import com.ukhanov.RealHelpDesk.core.security.auth.logout.service.LogoutService;
import com.ukhanov.RealHelpDesk.core.security.auth.register.dto.RegisterRequest;
import com.ukhanov.RealHelpDesk.core.security.auth.register.exception.RegistrationException;
import com.ukhanov.RealHelpDesk.core.security.auth.register.service.RegistrationService;
import com.ukhanov.RealHelpDesk.core.security.auth.tokens.dto.RefreshTokenRequest;
import com.ukhanov.RealHelpDesk.core.security.auth.tokens.dto.TokenBearerRequest;
import com.ukhanov.RealHelpDesk.core.security.auth.tokens.dto.TokenBearerResponse;
import com.ukhanov.RealHelpDesk.core.security.auth.tokens.dto.TokenStatusResponse;
import com.ukhanov.RealHelpDesk.core.security.auth.tokens.dto.TokensResponse;
import com.ukhanov.RealHelpDesk.core.security.auth.tokens.exception.TokenException;
import com.ukhanov.RealHelpDesk.core.security.auth.tokens.service.GetTokenService;
import com.ukhanov.RealHelpDesk.core.security.auth.tokens.service.RefreshTokenService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final LoginService loginService;
    private final LogoutService logoutService;
    private final GetTokenService getTokenService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(RegistrationService registrationService,
                          LoginService loginService,
                          LogoutService logoutService,
                          GetTokenService getTokenService,
                          RefreshTokenService refreshTokenService) {
        this.registrationService = registrationService;
        this.loginService = loginService;
        this.logoutService = logoutService;
        this.getTokenService = getTokenService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/register")
    public ResponseEntity<TokensResponse> registration(@Valid @RequestBody RegisterRequest registerRequest) {
        TokensResponse tokensResponse = registrationService.processRegistration(registerRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(tokensResponse);
    }

    @PostMapping("/login")
    public ResponseEntity<TokensResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        TokensResponse tokensResponse = loginService.processLogin(loginRequest);
        return ResponseEntity.status(HttpStatus.OK).body(tokensResponse);
    }

    @PostMapping("/token/refresh")
    public ResponseEntity<TokenBearerResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest refreshTokenRequest) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(refreshTokenService.refreshAccessToken(refreshTokenRequest));
    }

    // Проверка статуса refresh-токена и его срока действия
    @GetMapping("/token")
    public ResponseEntity<TokenStatusResponse> statusToken(@RequestBody TokenBearerRequest token) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(getTokenService.getStatusRefreshToken(token));
    }

    // Отзыв токена
    @DeleteMapping("/token")
    public ResponseEntity<LogoutResponse> revokeToken(@RequestBody TokenBearerRequest token) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(logoutService.processLogout(token));
    }

    @ExceptionHandler({RegistrationException.class, TokenException.class, LogoutException.class})
    public ResponseEntity<Map<String, String>> handleAuthExceptions(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }
}
