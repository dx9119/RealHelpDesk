package com.ukhanov.realhelpdesk.core.security.captcha.service;

import com.google.code.kaptcha.impl.DefaultKaptcha;
import com.ukhanov.realhelpdesk.core.security.captcha.dto.DtoCaptchaProperties;
import com.ukhanov.realhelpdesk.core.security.captcha.exception.CaptchaException;
import com.ukhanov.realhelpdesk.core.security.captcha.utils.CaptchaStorage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CaptchaServiceTest {

    private final CaptchaService captchaEnabled =
            new CaptchaService(new DefaultKaptcha(), new DtoCaptchaProperties(true));

    private final CaptchaService captchaDisabled =
            new CaptchaService(new DefaultKaptcha(), new DtoCaptchaProperties(false));

    @Test
    void капчаВыключена_проверкаПропускаетсяБезКода() {
        assertDoesNotThrow(() -> captchaDisabled.captVerificationResult(null, null));
        assertDoesNotThrow(() -> captchaDisabled.captVerificationResult("cap-off", null));
    }

    @Test
    void капчаВыключена_любойКодПринимается() {
        assertDoesNotThrow(() -> captchaDisabled.captVerificationResult("cap-off-code", "любой-код"));
    }

    @Test
    void правильныйКодПроходит() {
        CaptchaStorage.put("cap-right", "ABCD");

        assertDoesNotThrow(() -> captchaEnabled.captVerificationResult("cap-right", "abcd"));
    }

    @Test
    void неправильныйКодОтклоняется() {
        CaptchaStorage.put("cap-wrong", "ABCD");

        assertThrows(CaptchaException.class,
                () -> captchaEnabled.captVerificationResult("cap-wrong", "ABCE"));
    }

    @Test
    void пустойКодОтклоняется() {
        assertThrows(CaptchaException.class,
                () -> captchaEnabled.captVerificationResult("cap-empty", null));
    }

    @Test
    void неизвестныйCapIdОтклоняется() {
        assertThrows(CaptchaException.class,
                () -> captchaEnabled.captVerificationResult("cap-unknown", "ABCD"));
    }

    @Test
    void кодИспользуетсяТолькоОдинРаз() {
        CaptchaStorage.put("cap-single-use", "ABCD");

        assertDoesNotThrow(() -> captchaEnabled.captVerificationResult("cap-single-use", "ABCD"));
        assertThrows(CaptchaException.class,
                () -> captchaEnabled.captVerificationResult("cap-single-use", "ABCD"));
    }
}
