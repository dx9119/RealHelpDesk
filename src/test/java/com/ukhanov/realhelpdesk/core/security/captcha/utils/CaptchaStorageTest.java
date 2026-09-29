package com.ukhanov.realhelpdesk.core.security.captcha.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CaptchaStorageTest {

    private static final long DEFAULT_TTL_MILLIS = CaptchaStorage.ttlMillis;

    @AfterEach
    void restoreTtl() {
        CaptchaStorage.ttlMillis = DEFAULT_TTL_MILLIS;
    }

    @Test
    void codeReturned_beforeTtlExpires() {
        CaptchaStorage.put("cap-ok", "12345");

        assertEquals("12345", CaptchaStorage.get("cap-ok"));
    }

    @Test
    void expiredCode_notReturned() throws InterruptedException {
        CaptchaStorage.ttlMillis = 30;
        CaptchaStorage.put("cap-expired-get", "12345");
        Thread.sleep(60);

        assertNull(CaptchaStorage.get("cap-expired-get"));
    }

    @Test
    void expiredCode_cannotBeVerified() throws InterruptedException {
        CaptchaStorage.ttlMillis = 30;
        CaptchaStorage.put("cap-expired-remove", "12345");
        Thread.sleep(60);

        assertNull(CaptchaStorage.remove("cap-expired-remove"));
    }

    @Test
    void codeUsed_onlyOnce() {
        CaptchaStorage.put("cap-once", "54321");

        assertEquals("54321", CaptchaStorage.remove("cap-once"));
        assertNull(CaptchaStorage.get("cap-once"));
        assertNull(CaptchaStorage.remove("cap-once"));
    }

    @Test
    void secondPut_updatesCodeAndTtl() {
        CaptchaStorage.put("cap-update", "11111");
        CaptchaStorage.put("cap-update", "22222");

        assertEquals("22222", CaptchaStorage.get("cap-update"));
    }
}
