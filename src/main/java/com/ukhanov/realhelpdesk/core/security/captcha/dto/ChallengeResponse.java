package com.ukhanov.realhelpdesk.core.security.captcha.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Задание на решение: PoW (клиент хэширует {@code salt + ":" + nonce} до ведущих нулевых бит SHA-256) или эмодзи-задание (клиент выбирает
 * ячейки сетки и отправляет их индексы).
 */
public record ChallengeResponse(String challengeId, String type, Params params, Instant expiresAt) {

    /** Уровни капчи: Proof-of-Work и эскалационное эмодзи-задание. */
    public static final String TYPE_POW = "pow";
    public static final String TYPE_EMOJI = "emoji";

    /**
     * Параметры задания: pow несёт salt и difficulty, emoji — prompt и cells; непустые для своего уровня поля (null не сериализуются, чтобы
     * клиент видел ровно свой набор).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Params(String salt, Integer difficulty, String prompt, List<String> cells) {

        public static Params pow(String salt, int difficulty) {
            return new Params(salt, difficulty, null, null);
        }

        public static Params emoji(String prompt, List<String> cells) {
            return new Params(null, null, prompt, cells);
        }
    }
}
