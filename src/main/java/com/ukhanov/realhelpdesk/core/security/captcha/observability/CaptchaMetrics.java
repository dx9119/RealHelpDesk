package com.ukhanov.realhelpdesk.core.security.captcha.observability;

import org.springframework.stereotype.Component;

import com.ukhanov.realhelpdesk.core.security.captcha.exception.CaptchaReason;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Метрики капчи: выдачи challenge и итоги проверки с тегами reason — по ним видно, отсекает ли PoW ботов (pow_failed), ловит ли replay
 * (replayed) и не включён ли флаг fail-open (успехи при выключенной капче — отдельная метрика не нужна, смотрится в связке с
 * captcha.challenge.issued).
 */
@Component
public class CaptchaMetrics {

    /** Тип, пока один: PoW. Оставляем поле тега для эмодзи-уровня/провайдера. */
    static final String TYPE_POW = "pow";

    private final MeterRegistry registry;

    public CaptchaMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** Выдан challenge (POST /challenge). */
    public void challengeIssued() {
        Counter.builder("captcha.challenge.issued").description("Сколько challenge выдано клиентам").tag("type", TYPE_POW)
                .register(registry).increment();
    }

    /** Итог проверки токена на бизнес-операции: reason=ok для успеха. */
    public void verification(boolean success, CaptchaReason reason) {
        Counter.builder("captcha.verification").description("Результаты проверки captcha-токена").tag("type", TYPE_POW)
                .tag("result", success ? "success" : "failure").tag("reason", reason.code()).register(registry).increment();
    }
}
