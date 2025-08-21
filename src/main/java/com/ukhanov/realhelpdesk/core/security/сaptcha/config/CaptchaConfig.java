package com.ukhanov.realhelpdesk.core.security.сaptcha.config;

import com.google.code.kaptcha.impl.DefaultKaptcha;
import com.google.code.kaptcha.util.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

@Configuration
public class CaptchaConfig {

    @Bean
    public DefaultKaptcha captchaProducer() {
        Properties props = new Properties();

        // Общий стиль
        props.setProperty("kaptcha.border", "no");
        props.setProperty("kaptcha.background.clear.from", "192,224,255");
        props.setProperty("kaptcha.background.clear.to", "153,221,255");

        // Текст
        props.setProperty("kaptcha.textproducer.font.color", "white");
        props.setProperty("kaptcha.textproducer.char.space", "4");
        props.setProperty("kaptcha.textproducer.char.length", "5");
        props.setProperty("kaptcha.textproducer.font.size", "42");
        props.setProperty("kaptcha.textproducer.font.names", "Arial,Courier");

        // Размер изображения
        props.setProperty("kaptcha.image.width", "220");
        props.setProperty("kaptcha.image.height", "60");

        // Эффекты
        props.setProperty("kaptcha.noise.color", "102,204,255");
        props.setProperty("kaptcha.noise.impl", "com.google.code.kaptcha.impl.NoNoise");
        props.setProperty("kaptcha.obscurificator.impl", "com.google.code.kaptcha.impl.WaterRipple");

        Config config = new Config(props);
        DefaultKaptcha kaptcha = new DefaultKaptcha();
        kaptcha.setConfig(config);
        return kaptcha;
    }
}
