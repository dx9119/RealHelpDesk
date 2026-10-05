package com.ukhanov.realhelpdesk.core.security.captcha.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Настройка капчи из конфигурации: включена она или выключена ({@code CAPTCHA_ENABLED}).
 *
 * <p>
 * Значение уходит в {@code DtoCaptchaProperties} — так биндинг тестируется отдельно от логики капчи.
 * </p>
 */
@Component
@ConfigurationProperties(prefix = "captcha")
public class CaptchaProperties {

    /** Капча включена (true) или выключена (false). */
    private Boolean enabled;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
