package com.ukhanov.realhelpdesk.core.security.сaptcha.service;

import com.google.code.kaptcha.impl.DefaultKaptcha;
import com.ukhanov.realhelpdesk.core.security.сaptcha.exception.CaptchaException;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static com.ukhanov.realhelpdesk.core.security.сaptcha.utils.CaptchaStorage.captchaMap;


@Service
public class CaptchaService {

    private final DefaultKaptcha captchaProducer;

    public CaptchaService(DefaultKaptcha captchaProducer) {
        this.captchaProducer = captchaProducer;
    }

    public String generateCaptchaText(String CapId) {
        String captchaText = captchaProducer.createText();
        captchaMap.put(CapId, captchaText);
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
        String captchaText = captchaMap.get(CapId);
        return Base64.getEncoder()
                .encodeToString(captchaText.getBytes(StandardCharsets.UTF_8));
    }

    public void captVerificationResult(String captchaId, String captCode) throws CaptchaException {
        String expectedCode = captchaMap.get(captchaId);

        if (expectedCode == null || !expectedCode.equalsIgnoreCase(captCode)) {
            throw new CaptchaException("Провал прохождения капчи");
        }

        captchaMap.remove(captchaId);
    }


}
