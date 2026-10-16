package com.ukhanov.realhelpdesk.core.security.captcha.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

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

    private CaptchaProperties bind(MockEnvironment environment) {
        return Binder.get(environment).bind("captcha", Bindable.of(CaptchaProperties.class)).get();
    }
}
