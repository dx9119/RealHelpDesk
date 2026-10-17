package com.ukhanov.realhelpdesk.core.security.localjwt.captcha.dto;

/**
 * Подписанный captcha-токен: клиент прикладывает его к регистрации или запросу кода (тело или заголовок {@code X-Captcha-Token}).
 */
public record SolveResponse(String captchaToken) {
}
