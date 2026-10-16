package com.ukhanov.realhelpdesk.core.security.captcha.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Запрос challenge: действие, на которое клиенту нужен токен ({@code register} или {@code email-code}).
 */
public record ChallengeRequest(
        @NotBlank(message = "action обязателен") @Size(max = 50, message = "action не длиннее 50 символов") String action) {
}
