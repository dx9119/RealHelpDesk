package com.ukhanov.realhelpdesk.core.config;

import java.util.Arrays;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.context.annotation.Configuration;

@Configuration
public class WhiteUrlConfig {
    public static final List<String> WHITE_LIST_URLS = Arrays.asList("/api/v1/auth/login", "/api/v1/auth/register",
            "/api/v1/auth/tokens/access", "/api/v1/captcha", "/api/v1/health", "/api/v1/users/password-resets",
            "/api/v1/users/password-resets/**");

    public static boolean isMyTelegramBot(HttpServletRequest request) {
        final String accessToken = "";
        String headerValue = request.getHeader("X-Telegram-Bot-Api-Token");
        return accessToken.equals(headerValue);
    }

}
