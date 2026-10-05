package com.ukhanov.realhelpdesk.core.security.captcha.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import com.ukhanov.realhelpdesk.core.security.captcha.dto.DtoCaptchaProperties;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Конфигурация капчи: CaptchaProperties")
class CaptchaPropertiesTest {

    @Test
    @DisplayName("captcha.enabled биндится из конфигурации")
    void bindsEnabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("captcha.enabled", "true");

        CaptchaProperties properties = bind(environment);

        assertThat(properties.getEnabled()).isTrue();
    }

    @Test
    @DisplayName("Без captcha.* биндинг не срабатывает")
    void emptyEnvironment_isNotBound() {
        BindResult<CaptchaProperties> result = Binder.get(new MockEnvironment()).bind("captcha", Bindable.of(CaptchaProperties.class));

        assertThat(result.isBound()).isFalse();
    }

    @Test
    @DisplayName("Значение уходит в DtoCaptchaProperties без дополнительной настройки")
    void forwardsEnabledToDto() {
        CaptchaProperties properties = new CaptchaProperties();
        properties.setEnabled(false);

        DtoCaptchaProperties dto = new CaptchaConfig().dtoCaptchaProperties(properties);

        assertThat(dto.captchaEnabled()).isFalse();
    }

    private CaptchaProperties bind(MockEnvironment environment) {
        return Binder.get(environment).bind("captcha", Bindable.of(CaptchaProperties.class)).get();
    }
}
