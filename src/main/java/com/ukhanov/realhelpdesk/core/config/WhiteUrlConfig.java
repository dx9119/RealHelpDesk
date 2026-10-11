package com.ukhanov.realhelpdesk.core.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.context.annotation.Configuration;

@Configuration
public class WhiteUrlConfig {
    public static final List<String> WHITE_LIST_URLS = Arrays.asList("/api/v1/auth/login", "/api/v1/auth/register",
            "/api/v1/auth/tokens/access", "/api/v1/captcha", "/api/v1/health", "/api/v1/users/password-resets",
            "/api/v1/users/password-resets/**",
            // Health-пробы оркестратора (infraReview.md, п. 1): compose healthcheck и k8s liveness/readiness
            // ходят сюда без токена; /actuator/prometheus в белый список НЕ попадает — скрейп метрик идёт
            // через аутентификацию или внутреннюю сеть.
            "/actuator/health", "/actuator/health/**",
            // OpenAPI: спека и Swagger UI доступны без токена — контракт не секрет, а клиенту (и CI)
            // удобно забирать /v3/api-docs без аутентификации. Если это нежелательно, пути убираются
            // отсюда, и клиент ходит за спекой с access-cookie.
            "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**");

}
