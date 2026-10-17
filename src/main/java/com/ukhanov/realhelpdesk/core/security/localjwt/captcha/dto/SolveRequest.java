package com.ukhanov.realhelpdesk.core.security.localjwt.captcha.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Решение challenge: подобранный клиентом nonce (для PoW) или выбранные индексы ячеек (для эмодзи-задания). Клиент присылает то, что
 * соответствует уровню задания из challenge; отсутствие нужного поля — отказ капчи (pow_failed или wrong_answer), а не ошибка валидации
 * формы.
 */
public record SolveRequest(
        @NotBlank(message = "challengeId обязателен")
         @Size(max = 4_000, message = "challengeId не длиннее 4000 символов") String challengeId,
        @Size(max = 100, message = "nonce не длиннее 100 символов") String nonce,
        @Size(max = 16, message = "answer не длиннее 16 индексов") List<Integer> answer) {
}
