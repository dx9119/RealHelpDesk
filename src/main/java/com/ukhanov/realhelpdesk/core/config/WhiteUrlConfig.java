package com.ukhanov.realhelpdesk.core.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Configuration
public class WhiteUrlConfig {
    public static final List<String> WHITE_LIST_URLS = Arrays.asList(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/health-check",
            "/api/v1/telegram/**"
    );

    public static boolean isMyTelegramBot(HttpServletRequest request) {
        final String accessToken = "";
        String headerValue = request.getHeader("X-Telegram-Bot-Api-Token");
        return accessToken.equals(headerValue);
    }



}
