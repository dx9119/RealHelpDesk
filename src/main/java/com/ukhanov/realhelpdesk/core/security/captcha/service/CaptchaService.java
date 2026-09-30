package com.ukhanov.realhelpdesk.core.security.captcha.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.google.code.kaptcha.impl.DefaultKaptcha;
import com.ukhanov.realhelpdesk.core.security.captcha.dto.DtoCaptchaProperties;
import com.ukhanov.realhelpdesk.core.security.captcha.exception.CaptchaException;
import com.ukhanov.realhelpdesk.core.security.captcha.utils.CaptchaStorage;

@Service
public class CaptchaService {

    private final DefaultKaptcha captchaProducer;
    private final DtoCaptchaProperties captchaProperties;
    private static final Logger logger = LoggerFactory.getLogger(CaptchaService.class);

    public CaptchaService(DefaultKaptcha captchaProducer, DtoCaptchaProperties captchaProperties) {
        this.captchaProducer = captchaProducer;
        this.captchaProperties = captchaProperties;
    }

    public String generateCaptchaText(String capId) {
        String captchaText = captchaProducer.createText();
        CaptchaStorage.put(capId, captchaText);
        logger.debug("Сгенерирована капча, capId={}", capId);
        return captchaText;
    }

    public byte[] getImageBytes(String capId) throws IOException {
        BufferedImage captchaImage = captchaProducer.createImage(generateCaptchaText(capId));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(captchaImage, "jpg", baos);
        return baos.toByteArray();
    }

    public void captVerificationResult(String captchaId, String captCode) throws CaptchaException {
        if (!Boolean.TRUE.equals(captchaProperties.captchaEnabled())) {
            if (captchaId != null) {
                CaptchaStorage.remove(captchaId);
            }
            return;
        }

        String expectedCode = captchaId == null ? null : CaptchaStorage.remove(captchaId);

        if (expectedCode == null || captCode == null || !expectedCode.equalsIgnoreCase(captCode)) {
            throw new CaptchaException("Провал прохождения капчи");
        }
    }

}
