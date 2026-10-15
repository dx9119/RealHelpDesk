package com.ukhanov.realhelpdesk.core.security.captcha.service;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.ukhanov.realhelpdesk.core.observability.AuthMetrics;
import com.ukhanov.realhelpdesk.core.security.captcha.dto.DtoCaptchaProperties;
import com.ukhanov.realhelpdesk.core.security.captcha.exception.CaptchaException;
import com.ukhanov.realhelpdesk.core.security.captcha.generator.CaptchaGenerator;
import com.ukhanov.realhelpdesk.core.security.captcha.utils.CaptchaStorage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class CaptchaServiceTest {

    private final CaptchaService captchaEnabled = new CaptchaService(new CaptchaGenerator(), new DtoCaptchaProperties(true),
            new AuthMetrics(new SimpleMeterRegistry()));

    private final CaptchaService captchaDisabled = new CaptchaService(new CaptchaGenerator(), new DtoCaptchaProperties(false),
            new AuthMetrics(new SimpleMeterRegistry()));

    @Test
    void captchaDisabled_verificationSkippedWithoutCode() {
        assertDoesNotThrow(() -> captchaDisabled.captVerificationResult(null, null));
        assertDoesNotThrow(() -> captchaDisabled.captVerificationResult("cap-off", null));
    }

    @Test
    void captchaDisabled_anyCodeAccepted() {
        assertDoesNotThrow(() -> captchaDisabled.captVerificationResult("cap-off-code", "любой-код"));
    }

    @Test
    void validCode_passes() {
        CaptchaStorage.put("cap-right", "ABCD");

        assertDoesNotThrow(() -> captchaEnabled.captVerificationResult("cap-right", "abcd"));
    }

    @Test
    void invalidCode_rejected() {
        CaptchaStorage.put("cap-wrong", "ABCD");

        assertThrows(CaptchaException.class, () -> captchaEnabled.captVerificationResult("cap-wrong", "ABCE"));
    }

    @Test
    void blankCode_rejected() {
        assertThrows(CaptchaException.class, () -> captchaEnabled.captVerificationResult("cap-empty", null));
    }

    @Test
    void unknownCapId_rejected() {
        assertThrows(CaptchaException.class, () -> captchaEnabled.captVerificationResult("cap-unknown", "ABCD"));
    }

    @Test
    void codeUsed_onlyOnce() {
        CaptchaStorage.put("cap-single-use", "ABCD");

        assertDoesNotThrow(() -> captchaEnabled.captVerificationResult("cap-single-use", "ABCD"));
        assertThrows(CaptchaException.class, () -> captchaEnabled.captVerificationResult("cap-single-use", "ABCD"));
    }

    @Test
    void getImageBytes_jpegWithCodeInStorage() throws IOException {
        byte[] imageBytes = captchaEnabled.getImageBytes("cap-image");

        assertTrue(imageBytes.length > 0);
        assertNotNull(CaptchaStorage.remove("cap-image"));
    }
}
