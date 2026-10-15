package com.ukhanov.realhelpdesk.core.security.captcha.generator;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.security.SecureRandom;
import java.util.List;

/**
 * Генератор капчи: случайный код + отрисовка JPEG-картинки.
 *
 * <p>
 * Замена внешней библиотеки kaptcha: текст, фон, шрифт, искажение «водяными волнами» и случайные повороты символов рисуются здесь же, без
 * внешних зависимостей.
 * </p>
 */
public class CaptchaGenerator {

    /** Алфавит без похожих символов (0/O, 1/l/I), сверка регистронезависимая. */
    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

    private static final int CHAR_LENGTH = 5;
    private static final int CHAR_SPACE = 4;
    private static final int IMAGE_WIDTH = 220;
    private static final int IMAGE_HEIGHT = 60;
    private static final int FONT_SIZE = 42;

    /** Шрифты перебираются по порядку; если ни один не установлен — берётся системный sans-serif. */
    private static final List<String> FONT_NAMES = List.of("Arial", "Courier");

    private static final Color FONT_COLOR = Color.WHITE;
    private static final Color BACKGROUND_FROM = new Color(192, 224, 255);
    private static final Color BACKGROUND_TO = new Color(153, 221, 255);

    /** Максимальный поворот символа в радианах (~14°) и сдвиг по вертикали в пикселях. */
    private static final double MAX_CHAR_ROTATION = 0.25;
    private static final int MAX_CHAR_Y_SHIFT = 4;

    /** Параметры волнового искажения: амплитуда в пикселях, длина волны и границы фазы. */
    private static final double RIPPLE_AMPLITUDE = 2.0;
    private static final double RIPPLE_WAVELENGTH = 18.0;
    private static final double RIPPLE_PHASE_LIMIT = Math.PI * 2;

    private final SecureRandom random = new SecureRandom();

    /** Случайный код капчи той же длины, что рисуется на картинке. */
    public String createText() {
        StringBuilder text = new StringBuilder(CHAR_LENGTH);
        for (int i = 0; i < CHAR_LENGTH; i++) {
            text.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return text.toString();
    }

    /** Картинка капчи с текстом: градиентный фон, символы, волновое искажение. */
    public BufferedImage createImage(String text) {
        BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            paintBackground(graphics);
            paintText(graphics, text);
        } finally {
            graphics.dispose();
        }
        return applyRipple(image);
    }

    private void paintBackground(Graphics2D graphics) {
        GradientPaint background = new GradientPaint(0, 0, BACKGROUND_FROM, 0, IMAGE_HEIGHT, BACKGROUND_TO);
        graphics.setPaint(background);
        graphics.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
    }

    private void paintText(Graphics2D graphics, String text) {
        Font font = resolveFont();
        graphics.setFont(font);
        graphics.setColor(FONT_COLOR);
        FontMetrics metrics = graphics.getFontMetrics(font);

        int x = Math.max(0, (IMAGE_WIDTH - measureWidth(metrics, text)) / 2);
        int baseline = (IMAGE_HEIGHT + metrics.getAscent() - metrics.getDescent()) / 2;

        for (int i = 0; i < text.length(); i++) {
            char symbol = text.charAt(i);
            int symbolWidth = metrics.charWidth(symbol);
            int yShift = random.nextInt(MAX_CHAR_Y_SHIFT * 2 + 1) - MAX_CHAR_Y_SHIFT;
            double rotation = (random.nextDouble() * 2 - 1) * MAX_CHAR_ROTATION;
            double pivotX = x + symbolWidth / 2.0;
            double pivotY = baseline + yShift;

            AffineTransform original = graphics.getTransform();
            graphics.rotate(rotation, pivotX, pivotY);
            graphics.drawString(String.valueOf(symbol), x, baseline + yShift);
            graphics.setTransform(original);

            x += symbolWidth + CHAR_SPACE;
        }
    }

    private int measureWidth(FontMetrics metrics, String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += metrics.charWidth(text.charAt(i)) + CHAR_SPACE;
        }
        return width - CHAR_SPACE;
    }

    private Font resolveFont() {
        for (String name : FONT_NAMES) {
            Font candidate = new Font(name, Font.BOLD, FONT_SIZE);
            if (candidate.getFamily().equalsIgnoreCase(name)) {
                return candidate;
            }
        }
        return new Font(Font.SANS_SERIF, Font.BOLD, FONT_SIZE);
    }

    /**
     * Искажение «водяными волнами»: каждый пиксель смещается по синусоиде от центра картинки. Фаза случайна, поэтому изображение меняется
     * от запроса к запросу.
     */
    private BufferedImage applyRipple(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] sourcePixels = source.getRGB(0, 0, width, height, null, 0, width);
        int[] ripplePixels = new int[sourcePixels.length];

        double centerX = width / 2.0;
        double centerY = height / 2.0;
        double phase = random.nextDouble() * RIPPLE_PHASE_LIMIT;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double dx = x - centerX;
                double dy = y - centerY;
                double distance = Math.sqrt(dx * dx + dy * dy);
                if (distance < 1) {
                    ripplePixels[y * width + x] = sourcePixels[y * width + x];
                    continue;
                }
                double shift = RIPPLE_AMPLITUDE * Math.sin(distance * 2 * Math.PI / RIPPLE_WAVELENGTH + phase);
                int sourceX = clamp((int) Math.round(x + dx / distance * shift), width);
                int sourceY = clamp((int) Math.round(y + dy / distance * shift), height);
                ripplePixels[y * width + x] = sourcePixels[sourceY * width + sourceX];
            }
        }

        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        result.setRGB(0, 0, width, height, ripplePixels, 0, width);
        return result;
    }

    private int clamp(int value, int limit) {
        if (value < 0) {
            return 0;
        }
        if (value >= limit) {
            return limit - 1;
        }
        return value;
    }
}
