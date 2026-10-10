package com.ukhanov.realhelpdesk.core.security.ratelimit.config;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitPropertiesTest {

    @Test
    void requireReturnsLimitByKey() {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setLimits(Map.of("captcha", new RateLimitProperties.Limit(30, 60)));

        RateLimitProperties.Limit limit = properties.require("captcha");

        assertThat(limit.requests()).isEqualTo(30);
        assertThat(limit.windowSeconds()).isEqualTo(60);
    }

    @Test
    void requireFailsWhenKeyIsMissing() {
        RateLimitProperties properties = new RateLimitProperties();

        assertThatThrownBy(() -> properties.require("auth-login")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ratelimit.limits.auth-login");
    }

    @Test
    void limitRejectsNonPositiveValues() {
        assertThatThrownBy(() -> new RateLimitProperties.Limit(0, 60)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requests");
        assertThatThrownBy(() -> new RateLimitProperties.Limit(10, 0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("windowSeconds");
    }

    @Test
    void bindsLimitsFromConfigProperties() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("ratelimit.limits.captcha.requests", "30");
        environment.setProperty("ratelimit.limits.captcha.window-seconds", "60");

        RateLimitProperties properties = Binder.get(environment).bind("ratelimit", Bindable.of(RateLimitProperties.class)).get();

        assertThat(properties.require("captcha")).isEqualTo(new RateLimitProperties.Limit(30, 60));
    }
}
