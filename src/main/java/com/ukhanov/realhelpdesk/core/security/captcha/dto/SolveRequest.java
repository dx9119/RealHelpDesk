package com.ukhanov.realhelpdesk.core.security.captcha.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Решение challenge: подобранный клиентом nonce к исходному challengeId.
 */
public record SolveRequest(
        @NotBlank(message = "challengeId обязателен")
         @Size(max = 4_000, message = "challengeId не длиннее 4000 символов") String challengeId,
        @NotBlank(message = "nonce обязателен") @Size(max = 100, message = "nonce не длиннее 100 символов") String nonce) {
}
