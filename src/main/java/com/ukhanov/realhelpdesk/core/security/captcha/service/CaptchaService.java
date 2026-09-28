package com.ukhanov.realhelpdesk.core.security.captcha.service;

import com.google.code.kaptcha.impl.DefaultKaptcha;
import com.ukhanov.realhelpdesk.core.mail.service.EmailDeliveryService;
import com.ukhanov.realhelpdesk.core.security.captcha.dto.DtoCaptchaProperties;
import com.ukhanov.realhelpdesk.core.security.captcha.exception.CaptchaException;
import com.ukhanov.realhelpdesk.core.security.captcha.utils.CaptchaStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;


@Service
public class CaptchaService {

    private final DefaultKaptcha captchaProducer;
    private final DtoCaptchaProperties captchaProperties;
    private static final Logger logger = LoggerFactory.getLogger(CaptchaService.class);

    public CaptchaService(DefaultKaptcha captchaProducer, DtoCaptchaProperties captchaProperties) {
        this.captchaProducer = captchaProducer;
        this.captchaProperties = captchaProperties;
    }

    public String generateCaptchaText(String CapId) {
        String captchaText = captchaProducer.createText();
        CaptchaStorage.put(CapId, captchaText);
        logger.info("Сгенерирована капча для capId: {}", CapId);
        return captchaText;
    }

    public byte[] getImageBytes(String CapId) throws IOException {
        BufferedImage captchaImage = captchaProducer.createImage(
                generateCaptchaText(CapId)
        );
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(captchaImage, "jpg", baos);
        return baos.toByteArray();
    }

    public String getEncodedCaptchaText(String CapId) {
        String captchaText = CaptchaStorage.get(CapId);
        return Base64.getEncoder()
                .encodeToString(captchaText.getBytes(StandardCharsets.UTF_8));
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
