package com.ukhanov.realhelpdesk.core.security.captcha.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ukhanov.realhelpdesk.core.security.captcha.config.CaptchaProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Риск-движок капчи: счётчик провалов по IP и порог эскалации")
class CaptchaRiskServiceTest {

    private CaptchaRiskService riskService;

    @BeforeEach
    void setUp() {
        riskService = new CaptchaRiskService(properties(3, 600));
    }

    @Test
    @DisplayName("До порога провалов IP не рискованный; с порогом — рискованный")
    void threshold_escalates() {
        riskService.recordFailure("1.2.3.4");
        riskService.recordFailure("1.2.3.4");
        assertThat(riskService.isRisky("1.2.3.4")).isFalse();

        riskService.recordFailure("1.2.3.4");
        assertThat(riskService.isRisky("1.2.3.4")).isTrue();
    }

    @Test
    @DisplayName("Провалы одного IP не делают рискованным другой IP")
    void counters_arePerIp() {
        riskService.recordFailure("1.2.3.4");
        riskService.recordFailure("1.2.3.4");
        riskService.recordFailure("1.2.3.4");

        assertThat(riskService.isRisky("5.6.7.8")).isFalse();
        assertThat(riskService.isRisky("1.2.3.4")).isTrue();
    }

    @Test
    @DisplayName("Успех сбрасывает счётчик: после reset IP снова не рискованный")
    void reset_clearsCounter() {
        riskService.recordFailure("1.2.3.4");
        riskService.recordFailure("1.2.3.4");
        riskService.recordFailure("1.2.3.4");

        riskService.reset("1.2.3.4");

        assertThat(riskService.isRisky("1.2.3.4")).isFalse();
    }

    @Test
    @DisplayName("Неизвестный IP и null не рискованные; null в recordFailure/reset — no-op")
    void unknownIp_isNotRisky() {
        riskService.recordFailure(null);
        riskService.reset(null);

        assertThat(riskService.isRisky(null)).isFalse();
        assertThat(riskService.isRisky("9.9.9.9")).isFalse();
    }

    @Test
    @DisplayName("Невалидные порог/окно — старт падает с понятной ошибкой")
    void constructor_validatesProperties() {
        assertThatThrownBy(() -> new CaptchaRiskService(properties(0, 600))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("risk-failure-threshold");
        assertThatThrownBy(() -> new CaptchaRiskService(properties(null, 600))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("risk-failure-threshold");
        assertThatThrownBy(() -> new CaptchaRiskService(properties(5, 5))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("risk-failure-window-seconds");
        assertThatThrownBy(() -> new CaptchaRiskService(properties(5, null))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("risk-failure-window-seconds");
    }

    private CaptchaProperties properties(Integer threshold, Integer windowSeconds) {
        CaptchaProperties properties = new CaptchaProperties();
        properties.setRiskFailureThreshold(threshold);
        properties.setRiskFailureWindowSeconds(windowSeconds);
        return properties;
    }
}
