package com.ukhanov.realhelpdesk.core.security.captcha.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ukhanov.realhelpdesk.core.security.captcha.dto.DtoCaptchaProperties;
import com.ukhanov.realhelpdesk.core.security.captcha.generator.CaptchaGenerator;

@Configuration
public class CaptchaConfig {

    @Bean
    public DtoCaptchaProperties dtoCaptchaProperties(CaptchaProperties captchaProperties) {
        return new DtoCaptchaProperties(captchaProperties.getEnabled());
    }

    @Bean
    public CaptchaGenerator captchaGenerator() {
        return new CaptchaGenerator();
    }
}
