package com.ukhanov.realhelpdesk.core.security.ratelimit.interceptor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import com.ukhanov.realhelpdesk.core.log.LogSanitizer;
import com.ukhanov.realhelpdesk.core.security.ratelimit.annotation.RateLimit;
import com.ukhanov.realhelpdesk.core.security.ratelimit.config.RateLimitProperties;
import com.ukhanov.realhelpdesk.core.security.ratelimit.service.RateLimitService;

import tools.jackson.databind.ObjectMapper;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;
    private final RateLimitProperties rateLimitProperties;

    public RateLimitInterceptor(RateLimitService rateLimitService, ObjectMapper objectMapper, RateLimitProperties rateLimitProperties) {
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
        this.rateLimitProperties = rateLimitProperties;
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

        RateLimitProperties.Limit limit = rateLimitProperties.require(rateLimit.key());

        String client = clientKey(request);
        // ключ — по шаблону маршрута: /password-resets/123 и /password-resets/456 должны считаться одним лимитом
        String key = client + "|" + request.getMethod() + " " + routeOf(request);
        RateLimitService.Decision decision = rateLimitService.check(key, limit.requests(), Duration.ofSeconds(limit.windowSeconds()));

        if (decision.allowed()) {
            return true;
        }

        logger.warn("Превышен лимит запросов: {} {}, ip={}", request.getMethod(), LogSanitizer.uri(request.getRequestURI()), client);

        String detail = "Слишком много запросов. Повторите через " + decision.retryAfterSeconds() + " сек.";
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase());
        problem.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        problem.put("detail", detail);
        problem.put("instance", request.getRequestURI());
        problem.put("timestamp", Instant.now().toString());

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), problem);

        return false;
    }

    private String routeOf(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern != null ? pattern.toString() : request.getRequestURI();
    }

    private String clientKey(HttpServletRequest request) {
        if (rateLimitProperties.isTrustProxyHeaders()) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return forwardedFor.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
