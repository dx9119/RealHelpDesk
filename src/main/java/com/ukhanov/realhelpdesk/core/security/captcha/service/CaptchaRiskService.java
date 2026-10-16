package com.ukhanov.realhelpdesk.core.security.captcha.service;

import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ukhanov.realhelpdesk.core.security.captcha.config.CaptchaProperties;

/**
 * Локальный риск-движок капчи: считает провалы проверок по IP клиента за скользящее окно. Порог {@code captcha.risk-failure-threshold}
 * достигнут — {@link CaptchaService} выдаёт эмодзи-задание вместо PoW (эскалация). Счётчик сбрасывается по истечении окна (Caffeine
 * expireAfterWrite) и после любой успешной проверки — человек, прошедший капчу, снова получает обычный PoW. Внешние анти-бот провайдеры не
 * используются — только эти два числа из конфигурации.
 */
@Service
public class CaptchaRiskService {

    private static final long MAX_TRACKED_IPS = 50_000;

    private final int failureThreshold;
    private final Cache<String, Integer> failures;

    public CaptchaRiskService(CaptchaProperties properties) {
        Integer threshold = properties.getRiskFailureThreshold();
        Integer windowSeconds = properties.getRiskFailureWindowSeconds();
        if (threshold == null || threshold < 1 || threshold > 1_000) {
            throw new IllegalStateException("captcha.risk-failure-threshold должен быть в диапазоне 1..1000, получено " + threshold);
        }
        if (windowSeconds == null || windowSeconds < 10 || windowSeconds > 86_400) {
            throw new IllegalStateException(
                    "captcha.risk-failure-window-seconds должен быть в диапазоне 10..86400, получено " + windowSeconds);
        }
        this.failureThreshold = threshold;
        this.failures = Caffeine.newBuilder().expireAfterWrite(windowSeconds, TimeUnit.SECONDS).maximumSize(MAX_TRACKED_IPS).build();
    }

    /** Учтён очередной провал капчи с этого IP. */
    public void recordFailure(String clientIp) {
        if (isBlank(clientIp)) {
            return;
        }
        failures.asMap().merge(clientIp, 1, Integer::sum);
    }

    /** Достигнут ли порог провалов: да — клиенту положено эмодзи-задание вместо PoW. */
    public boolean isRisky(String clientIp) {
        if (isBlank(clientIp)) {
            return false;
        }
        Integer count = failures.getIfPresent(clientIp);
        return count != null && count >= failureThreshold;
    }

    /** Сброс счётчика IP после успешной проверки капчи. */
    public void reset(String clientIp) {
        if (isBlank(clientIp)) {
            return;
        }
        failures.invalidate(clientIp);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
