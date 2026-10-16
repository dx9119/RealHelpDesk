package com.ukhanov.realhelpdesk.core.security.captcha.dto;

import java.time.Instant;

/**
 * Задание на решение PoW: клиент хэширует {@code salt + ":" + nonce} и добивается ведущих нулевых бит SHA-256 не меньше {@code difficulty}.
 */
public record ChallengeResponse(String challengeId, String type, Params params, Instant expiresAt) {

    /** Единственный тип капчи сейчас — Proof-of-Work (поле сохраняет место для эмодзи-уровня). */
    public static final String TYPE_POW = "pow";

    public record Params(String salt, int difficulty) {
    }
}
