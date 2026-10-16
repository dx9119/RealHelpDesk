package com.ukhanov.realhelpdesk.core.security.captcha.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Настройка капчи из конфигурации: включение, HMAC-секрет подписи PoW-токенов, сложность Proof-of-Work и сроки (в секундах). Только биндинг
 * ключей {@code captcha.*} — валидация значений и сборка ключа подписи живут в {@link CaptchaConfig} и {@code CaptchaTokenService}, их
 * легко тестировать, подставляя готовый экземпляр.
 */
@Component
@ConfigurationProperties(prefix = "captcha")
@Getter
@Setter
public class CaptchaProperties {

    /** Капча включена (true) или выключена (false): выключена — проверка токена на регистрации пропускается. */
    private Boolean enabled;

    /** HMAC-секрет подписи challenge и токенов (минимум 32 символа). */
    private String hmacSecret;

    /** Сложность PoW — число ведущих нулевых бит SHA-256 (1..26). */
    private Integer powDifficulty;

    /** Срок жизни challenge от выдачи до решения, секунды. */
    private Integer challengeTtlSeconds;

    /** Срок жизни выданного токена от solve до отправки формы, секунды. */
    private Integer tokenTtlSeconds;

    /** Минимальное время от выдачи challenge до отправки формы, секунды (0 — проверка отключена). */
    private Integer minFillSeconds;
}
