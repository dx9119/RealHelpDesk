package com.ukhanov.realhelpdesk.core.security.auth;

import com.ukhanov.realhelpdesk.core.security.auth.login.dto.LoginRequest;
import com.ukhanov.realhelpdesk.core.security.auth.login.service.LoginService;
import com.ukhanov.realhelpdesk.core.security.auth.logout.dto.LogoutResponse;
import com.ukhanov.realhelpdesk.core.security.auth.logout.exception.LogoutException;
import com.ukhanov.realhelpdesk.core.security.auth.logout.service.LogoutService;
import com.ukhanov.realhelpdesk.core.security.auth.register.dto.RegisterRequest;
import com.ukhanov.realhelpdesk.core.security.auth.register.exception.RegistrationException;
import com.ukhanov.realhelpdesk.core.security.auth.register.service.RegistrationService;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.dto.TokenBearerRequest;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.dto.TokenStatusResponse;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.dto.TokensResponse;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.exception.TokenException;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.GetTokenService;
import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final LoginService loginService;
    private final LogoutService logoutService;
    private final GetTokenService getTokenService;

    public AuthController(RegistrationService registrationService,
                          LoginService loginService,
                          LogoutService logoutService,
                          GetTokenService getTokenService) {
        this.registrationService = registrationService;
        this.loginService = loginService;
        this.logoutService = logoutService;
        this.getTokenService = getTokenService;
    }

    @PostMapping("/register")
    public ResponseEntity<TokensResponse> registration(@Valid @RequestBody RegisterRequest registerRequest)
        throws RegistrationException, MessagingException {
        TokensResponse response = registrationService.processRegistration(registerRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<TokensResponse> login(@Valid @RequestBody LoginRequest loginRequest) throws TokenException {
        TokensResponse response = loginService.processLogin(loginRequest);
        return ResponseEntity.ok(response);
    }

    // Проверка статуса refresh-токена и его срока действия
    @GetMapping("/token")
    public ResponseEntity<TokenStatusResponse> statusToken(@RequestBody TokenBearerRequest token) throws TokenException {
        TokenStatusResponse response = getTokenService.getStatusRefreshToken(token);
        return ResponseEntity.ok(response);
    }

    // Отзыв токена
    @DeleteMapping("/token")
    public ResponseEntity<LogoutResponse> revokeToken(@RequestBody TokenBearerRequest token) throws LogoutException, TokenException {
        LogoutResponse response = logoutService.processLogout(token);
        return ResponseEntity.ok(response);
    }

}
