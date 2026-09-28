package com.ukhanov.realhelpdesk.core.security.ratelimit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitServiceTest {

    private RateLimitService service;

    @BeforeEach
    void setUp() {
        service = new RateLimitService();
    }

    @Test
    void запросыВПределахЛимитаПропускаются() {
        for (int i = 1; i <= 3; i++) {
            assertTrue(service.check("key", 3, Duration.ofMinutes(1)).allowed(), "запрос " + i);
        }
    }

    @Test
    void ЧетвертыйЗапросБлокируетсяСRetryAfter() {
        service.check("key", 3, Duration.ofMinutes(1));
        service.check("key", 3, Duration.ofMinutes(1));
        service.check("key", 3, Duration.ofMinutes(1));

        RateLimitService.Decision decision = service.check("key", 3, Duration.ofMinutes(1));

        assertFalse(decision.allowed());
        assertTrue(decision.retryAfterSeconds() >= 1);
        assertTrue(decision.retryAfterSeconds() <= 60);
    }

    @Test
    void РазныеКлючиНеЗависятДругОтДруга() {
        for (int i = 1; i <= 3; i++) {
            service.check("ip1", 3, Duration.ofMinutes(1));
        }

        assertFalse(service.check("ip1", 3, Duration.ofMinutes(1)).allowed());
        assertTrue(service.check("ip2", 3, Duration.ofMinutes(1)).allowed());
    }

    @Test
    void СоСпособомЗапросаКлючиНеЗависят() {
        for (int i = 1; i <= 2; i++) {
            service.check("ip|POST /api/v1/auth/login", 2, Duration.ofMinutes(1));
        }

        assertFalse(service.check("ip|POST /api/v1/auth/login", 2, Duration.ofMinutes(1)).allowed());
        assertTrue(service.check("ip|POST /api/v1/auth/register", 2, Duration.ofMinutes(1)).allowed());
    }

    @Test
    void СтрокоОкнаСбрасывается() throws InterruptedException {
        assertTrue(service.check("key", 1, Duration.ofMillis(30)).allowed());
        assertFalse(service.check("key", 1, Duration.ofMillis(30)).allowed());

        Thread.sleep(60);

        assertTrue(service.check("key", 1, Duration.ofMillis(30)).allowed());
    }

    @Test
    void ResetОчищаетСчетчики() {
        for (int i = 1; i <= 2; i++) {
            service.check("key", 2, Duration.ofMinutes(1));
        }
        assertFalse(service.check("key", 2, Duration.ofMinutes(1)).allowed());

        service.reset();

        assertTrue(service.check("key", 2, Duration.ofMinutes(1)).allowed());
    }
}
