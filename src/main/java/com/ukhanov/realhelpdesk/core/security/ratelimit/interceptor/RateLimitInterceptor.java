package com.ukhanov.realhelpdesk.core.security.ratelimit.interceptor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.ukhanov.realhelpdesk.core.security.ratelimit.annotation.RateLimit;
import com.ukhanov.realhelpdesk.core.security.ratelimit.service.RateLimitService;

import tools.jackson.databind.ObjectMapper;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    @Value("${ratelimit.trust-proxy-headers:false}")
    private boolean trustProxyHeaders;

    public RateLimitInterceptor(RateLimitService rateLimitService, ObjectMapper objectMapper) {
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (rateLimit == null) {
            return true;
        }

        String key = clientKey(request) + "|" + request.getMethod() + " " + request.getRequestURI();
        RateLimitService.Decision decision = rateLimitService.check(key, rateLimit.requests(),
                Duration.ofSeconds(rateLimit.windowSeconds()));

        if (decision.allowed()) {
            return true;
        }

        logger.warn("Rate limit превышен: {} {} (key={})", request.getMethod(), request.getRequestURI(), key);

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), Map.of("Сообщение",
                "Слишком много запросов. Повторите через " + decision.retryAfterSeconds() + " сек.", "Путь", request.getRequestURI()));

        return false;
    }

    private String clientKey(HttpServletRequest request) {
        if (trustProxyHeaders) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return forwardedFor.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
