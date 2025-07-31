package com.ukhanov.realhelpdesk.core.security.auth;

import com.ukhanov.realhelpdesk.core.mail.exception.EmailAccessDeniedException;
import com.ukhanov.realhelpdesk.core.security.auth.login.dto.LoginRequest;
import com.ukhanov.realhelpdesk.core.security.auth.login.service.LoginService;
import com.ukhanov.realhelpdesk.core.security.auth.logout.dto.LogoutResponse;
import com.ukhanov.realhelpdesk.core.security.auth.logout.exception.LogoutException;
import com.ukhanov.realhelpdesk.core.security.auth.logout.service.LogoutService;
import com.ukhanov.realhelpdesk.core.security.auth.register.dto.RegisterRequest;
import com.ukhanov.realhelpdesk.core.security.auth.register.exception.RegistrationException;
import com.ukhanov.realhelpdesk.core.security.auth.register.service.RegistrationService;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.dto.AuthorizationResponse;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.dto.TokenBearerRequest;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.dto.TokenStatusResponse;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.dto.TokensResponse;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.exception.TokenException;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.ChangeTokenService;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.GetTokenService;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final LoginService loginService;
    private final LogoutService logoutService;
    private final GetTokenService getTokenService;
    private final ChangeTokenService changeTokenService;

    public AuthController(RegistrationService registrationService,
                          LoginService loginService,
                          LogoutService logoutService,
                          GetTokenService getTokenService,
        ChangeTokenService changeTokenService) {
        this.registrationService = registrationService;
        this.loginService = loginService;
        this.logoutService = logoutService;
        this.getTokenService = getTokenService;
        this.changeTokenService = changeTokenService;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> registration(@Valid @RequestBody RegisterRequest registerRequest)
        throws RegistrationException, MessagingException, EmailAccessDeniedException {

        TokensResponse tokens = registrationService.processRegistration(registerRequest);

        ResponseCookie accessCookie = ResponseCookie.from("accessToken", tokens.getAccessToken())
            .httpOnly(true)
            .secure(true)
            .path("/")
            .maxAge(Duration.ofHours(1))
            .sameSite("None")
            .build();

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", tokens.getRefreshToken())
            .httpOnly(true)
            .secure(true)
            .path("/")
            .maxAge(Duration.ofDays(7))
            .sameSite("None")
            .build();

        Map<String, String> responseBody = Map.of(
            "status", "success",
            "message", "Регистрация прошла успешно"
        );

        return ResponseEntity
            .ok()
            .header(HttpHeaders.SET_COOKIE, accessCookie.toString(), refreshCookie.toString())
            .body(responseBody);
    }


    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@Valid @RequestBody LoginRequest loginRequest) throws TokenException {
        TokensResponse tokens = loginService.processLogin(loginRequest);

        ResponseCookie accessCookie = ResponseCookie.from("accessToken", tokens.getAccessToken())
            .httpOnly(true)
            .secure(true)
            .path("/")
            .maxAge(Duration.ofHours(1))
            .sameSite("None")
            .build();

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", tokens.getRefreshToken())
            .httpOnly(true)
            .secure(true)
            .path("/")
            .maxAge(Duration.ofDays(7))
            .sameSite("None")
            .build();

        Map<String, String> responseBody = Map.of(
            "status", "success",
            "message", "Вход выполнен успешно"
        );

        return ResponseEntity
            .ok()
            .header(HttpHeaders.SET_COOKIE, accessCookie.toString(), refreshCookie.toString())
            .body(responseBody);
    }


    // Проверка статуса refresh-токена и его срока действия
    @PostMapping("/token")
    public ResponseEntity<TokenStatusResponse> statusToken(HttpServletRequest request)
        throws TokenException, LogoutException {
        TokenStatusResponse response = getTokenService.getStatusRefreshTokenFromCookie(request);
        return ResponseEntity.ok(response);
    }

    // Проверка наличия авторизации на клиенте (фильтр не даст дойти до этого метода, если есть проблемы с авторизацией/токеном)
    @PostMapping("/check")
    public ResponseEntity<AuthorizationResponse> checkToken(HttpServletRequest request)
        throws TokenException {
        AuthorizationResponse response = new AuthorizationResponse("true");
        return ResponseEntity.ok(response);
    }

    // Отзыв токена
    @DeleteMapping("/token")
    public ResponseEntity<LogoutResponse> revokeToken(HttpServletRequest request) throws LogoutException, TokenException {
        LogoutResponse response = logoutService.processLogout(request);
        return ResponseEntity.ok(response);
    }


}
