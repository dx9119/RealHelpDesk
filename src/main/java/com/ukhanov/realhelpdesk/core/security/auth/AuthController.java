package com.ukhanov.realhelpdesk.core.security.auth;

import com.ukhanov.realhelpdesk.core.mail.exception.EmailAccessDeniedException;
import com.ukhanov.realhelpdesk.core.security.auth.login.dto.LoginRequest;
import com.ukhanov.realhelpdesk.core.security.auth.login.service.LoginService;
import com.ukhanov.realhelpdesk.core.security.auth.logout.exception.LogoutException;
import com.ukhanov.realhelpdesk.core.security.auth.logout.service.LogoutService;
import com.ukhanov.realhelpdesk.core.security.auth.register.dto.RegisterRequest;
import com.ukhanov.realhelpdesk.core.security.auth.register.exception.RegistrationException;
import com.ukhanov.realhelpdesk.core.security.auth.register.service.RegistrationService;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.dto.AuthorizationResponse;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.dto.TokenStatusResponse;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.dto.TokensResponse;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.exception.TokenException;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.GetTokenService;
import com.ukhanov.realhelpdesk.core.security.auth.refresh.exception.RefreshException;
import com.ukhanov.realhelpdesk.core.security.auth.refresh.service.RefreshService;
import com.ukhanov.realhelpdesk.core.security.сaptcha.exception.CaptchaException;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.io.UnsupportedEncodingException;
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
    private final RefreshService refreshService;

    public AuthController(RegistrationService registrationService,
                          LoginService loginService,
                          LogoutService logoutService,
                          GetTokenService getTokenService,
                          RefreshService refreshService) {
        this.registrationService = registrationService;
        this.loginService = loginService;
        this.logoutService = logoutService;
        this.getTokenService = getTokenService;
        this.refreshService = refreshService;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> registration(
            @Valid @RequestBody RegisterRequest registerRequest,
            @RequestParam String capId)
            throws RegistrationException, MessagingException, EmailAccessDeniedException, CaptchaException, UnsupportedEncodingException {

        TokensResponse tokens = registrationService.processRegistration(registerRequest, capId);

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
            .maxAge(Duration.ofDays(30))
            .sameSite("None")
            .build();

        Map<String, String> responseBody = Map.of(
            "Статус", "Успех",
            "Сообщение", "Регистрация прошла успешно"
        );

        return ResponseEntity
            .ok()
            .header(HttpHeaders.SET_COOKIE, accessCookie.toString(), refreshCookie.toString())
            .body(responseBody);
    }


    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(
            @Valid
            @RequestBody LoginRequest loginRequest,
            @CookieValue(value = "captcha", defaultValue = "") String captchaCookie) throws TokenException {
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
            .maxAge(Duration.ofDays(30))
            .sameSite("None")
            .build();

        Map<String, String> responseBody = Map.of(
            "Статус", "Успех",
            "Сообщение", "Вход выполнен успешно"
        );

        return ResponseEntity
            .ok()
            .header(HttpHeaders.SET_COOKIE, accessCookie.toString(), refreshCookie.toString())
            .body(responseBody);
    }

    // Отдать новый токен авторизации при наличии активного refresh token
    @PostMapping("update")
    public ResponseEntity<Map<String, String>> updateAuth (HttpServletRequest request) throws TokenException, RefreshException {

        ResponseCookie accessCookie = ResponseCookie.from("accessToken", refreshService.updateAccess(request))
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(Duration.ofHours(1))
                .sameSite("None")
                .build();

        Map<String,String> responseBody = Map.of("Статус","Успех");

        return ResponseEntity
                .ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
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
        AuthorizationResponse response = new AuthorizationResponse("Активен");
        return ResponseEntity.ok(response);
    }


    // удаляем куки если пользователь хочет завершить сессию
    @DeleteMapping("/cookies")
    public ResponseEntity<Map<String, String>> deleteCookies(HttpServletRequest request) throws TokenException, LogoutException {

        logoutService.processLogout(request);

        ResponseCookie accessCookie = ResponseCookie.from("accessToken", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(Duration.ZERO)
                .sameSite("None")
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(Duration.ZERO)
                .sameSite("None")
                .build();

        Map<String,String> responseBody = Map.of("Статус","Успех");

        return ResponseEntity
                .ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString(), refreshCookie.toString())
                .body(responseBody);
    }

}
