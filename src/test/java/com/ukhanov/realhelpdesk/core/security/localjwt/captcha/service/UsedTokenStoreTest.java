package com.ukhanov.realhelpdesk.core.security.localjwt.captcha.service;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.github.benmanes.caffeine.cache.Cache;
import com.ukhanov.realhelpdesk.core.security.localjwt.captcha.config.CaptchaProperties;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Одноразовость решений капчи: повтор того же id отклоняется")
class UsedTokenStoreTest {

    @Test
    @DisplayName("Первое использование проходит, повтор — нет; разные ключи независимы")
    void firstUse_rejectsReplay() {
        UsedTokenStore store = new UsedTokenStore(300);

        assertThat(store.firstUse("t:abc")).isTrue();
        assertThat(store.firstUse("t:abc")).isFalse();
        assertThat(store.firstUse("t:def")).isTrue();
        assertThat(store.firstUse("c:xyz")).isTrue();
        assertThat(store.firstUse("c:xyz")).isFalse();
    }

    @Test
    @DisplayName("Запись вытесняется после TTL — до истечения JWT всё равно отклоняет подпись/exp")
    void entries_expireAfterTtl() throws InterruptedException {
        UsedTokenStore store = new UsedTokenStore(1);

        assertThat(store.firstUse("t:once")).isTrue();
        Thread.sleep(1_200);
        assertThat(store.firstUse("t:once")).isTrue();
    }

    @Test
    @DisplayName("TTL кэша из свойств: max(challenge, token) + запас — не меньше срока жизни challenge")
    void ttlFromProperties() throws Exception {
        CaptchaProperties properties = new CaptchaProperties();
        properties.setChallengeTtlSeconds(600);
        properties.setTokenTtlSeconds(300);
        properties.setPowDifficulty(18);
        properties.setMinFillSeconds(0);

        UsedTokenStore store = new UsedTokenStore(properties);

        var field = UsedTokenStore.class.getDeclaredField("used");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Cache<String, Boolean> cache = (Cache<String, Boolean>) field.get(store);
        var expirePolicy = cache.policy().expireAfterWrite().orElseThrow();
        assertThat(expirePolicy.getExpiresAfter()).isEqualTo(Duration.ofSeconds(600 + UsedTokenStore.EXPIRE_MARGIN_SECONDS));
    }
}
