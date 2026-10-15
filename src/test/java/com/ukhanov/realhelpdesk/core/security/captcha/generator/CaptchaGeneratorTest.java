package com.ukhanov.realhelpdesk.core.security.captcha.generator;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptchaGeneratorTest {

    private static final String TEXT_PATTERN = "^[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{5}$";

    private final CaptchaGenerator generator = new CaptchaGenerator();

    @Test
    void createText_fixedLengthWithoutAmbiguousSymbols() {
        for (int i = 0; i < 50; i++) {
            assertTrue(generator.createText().matches(TEXT_PATTERN));
        }
    }

    @Test
    void createText_randomOnEveryCall() {
        assertFalse(generator.createText().equals(generator.createText()));
    }

    @Test
    void createImage_expectedSize() {
        BufferedImage image = generator.createImage("A2B3C");

        assertEquals(220, image.getWidth());
        assertEquals(60, image.getHeight());
    }

    @Test
    void createImage_distortedOnEveryRender() {
        BufferedImage first = generator.createImage("A2B3C");
        BufferedImage second = generator.createImage("A2B3C");

        assertFalse(Arrays.equals(pixels(first), pixels(second)));
    }

    @Test
    void createImage_writesReadableJpeg() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        assertTrue(ImageIO.write(generator.createImage("A2B3C"), "jpg", output));
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(output.toByteArray()));

        assertEquals(220, decoded.getWidth());
        assertEquals(60, decoded.getHeight());
    }

    private int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }
}
