package com.ukhanov.realhelpdesk.core.security.auth;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.time.Duration;

import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ukhanov.realhelpdesk.core.config.JwtProperties;
import com.ukhanov.realhelpdesk.core.mail.exception.EmailAccessDeniedException;
import com.ukhanov.realhelpdesk.core.security.auth.login.dto.LoginRequest;
import com.ukhanov.realhelpdesk.core.security.auth.login.service.LoginService;
import com.ukhanov.realhelpdesk.core.security.auth.logout.exception.LogoutException;
import com.ukhanov.realhelpdesk.core.security.auth.logout.service.LogoutService;
import com.ukhanov.realhelpdesk.core.security.auth.refresh.exception.RefreshException;
import com.ukhanov.realhelpdesk.core.security.auth.refresh.service.RefreshService;
import com.ukhanov.realhelpdesk.core.security.auth.register.dto.RegisterRequest;
import com.ukhanov.realhelpdesk.core.security.auth.register.exception.RegistrationException;
import com.ukhanov.realhelpdesk.core.security.auth.register.service.RegistrationService;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.dto.AuthorizationResponse;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.dto.TokenStatusResponse;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.dto.TokensResponse;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.exception.TokenException;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.GetTokenService;
import com.ukhanov.realhelpdesk.core.security.captcha.exception.CaptchaException;
import com.ukhanov.realhelpdesk.core.security.ratelimit.annotation.RateLimit;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String ACCESS_COOKIE = "accessToken";
    private static final String REFRESH_COOKIE = "refreshToken";
    private static final URI PROFILE_URI = URI.create("/api/v1/users/profile");

    // Refresh-cookie нужен только эндпоинтам этого контроллера — не отправляем его на весь API
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofHours(1);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);

    private final RegistrationService registrationService;
    private final LoginService loginService;
    private final LogoutService logoutService;
    private final GetTokenService getTokenService;
    private final RefreshService refreshService;
    private final JwtProperties jwtProperties;

    @PostMapping("/register")
    @RateLimit(key = "auth-register")
    public ResponseEntity<Void> registration(@Valid @RequestBody RegisterRequest registerRequest,
            @RequestParam(required = false) String capId)
            throws RegistrationException, MessagingException, EmailAccessDeniedException, CaptchaException, UnsupportedEncodingException {

        TokensResponse tokens = registrationService.processRegistration(registerRequest, capId);

        return ResponseEntity.created(PROFILE_URI)
                .header(HttpHeaders.SET_COOKIE, accessCookie(tokens.accessToken(), ACCESS_TOKEN_TTL).toString(),
                        refreshCookie(tokens.refreshToken(), REFRESH_TOKEN_TTL).toString())
                .build();
    }

    @PostMapping("/login")
    @RateLimit(key = "auth-login")
    public ResponseEntity<Void> login(@Valid @RequestBody LoginRequest loginRequest) throws TokenException {
        TokensResponse tokens = loginService.processLogin(loginRequest);

        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, accessCookie(tokens.accessToken(), ACCESS_TOKEN_TTL).toString(),
                refreshCookie(tokens.refreshToken(), REFRESH_TOKEN_TTL).toString()).build();
    }

    // Отдать новый токен авторизации при наличии активного refresh token
    @PostMapping("/tokens/access")
    @RateLimit(key = "auth-access-token")
    public ResponseEntity<Void> createAccessToken(HttpServletRequest request) throws TokenException, RefreshException {

        ResponseCookie accessCookie = accessCookie(refreshService.updateAccess(request), ACCESS_TOKEN_TTL);

        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, accessCookie.toString()).build();
    }

    // Проверка статуса refresh-токена и его срока действия
    @GetMapping("/tokens/refresh")
    public ResponseEntity<TokenStatusResponse> refreshTokenStatus(HttpServletRequest request) throws TokenException, LogoutException {
        TokenStatusResponse response = getTokenService.getStatusRefreshTokenFromCookie(request);
        return ResponseEntity.ok(response);
    }

    // Проверка наличия авторизации на клиенте (фильтр не даст дойти до этого метода, если есть проблемы с авторизацией/токеном)
    @GetMapping("/session")
    public ResponseEntity<AuthorizationResponse> sessionStatus(HttpServletRequest request) throws TokenException {
        AuthorizationResponse response = new AuthorizationResponse("Активен");
        return ResponseEntity.ok(response);
    }

    // удаляем куки если пользователь хочет завершить сессию
    @DeleteMapping("/session")
    public ResponseEntity<Void> logout(HttpServletRequest request) throws TokenException, LogoutException {

        logoutService.processLogout(request);

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, accessCookie("", Duration.ZERO).toString(), refreshCookie("", Duration.ZERO).toString())
                .build();
    }

    private ResponseCookie accessCookie(String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(ACCESS_COOKIE, value).httpOnly(true).secure(true).path("/")
                .maxAge(maxAge).sameSite(jwtProperties.getCookie().getSameSite().getValue());
        return withDomain(builder).build();
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(REFRESH_COOKIE, value).httpOnly(true).secure(true)
                .path(REFRESH_COOKIE_PATH).maxAge(maxAge).sameSite(jwtProperties.getCookie().getSameSite().getValue());
        return withDomain(builder).build();
    }

    /** Домен из jwt.cookie.domain: пусто — атрибут Domain не ставим, cookie остаётся host-only, как раньше. */
    private ResponseCookie.ResponseCookieBuilder withDomain(ResponseCookie.ResponseCookieBuilder builder) {
        String domain = jwtProperties.getCookie().getDomain();
        return domain != null ? builder.domain(domain) : builder;
    }

}
