package com.ukhanov.realhelpdesk.core.security.auth.tokens.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ukhanov.realhelpdesk.core.security.auth.tokens.dto.TokenCleanupResponse;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.RefreshTokenCleanupService;
import com.ukhanov.realhelpdesk.core.security.auth.tokens.service.RefreshTokenCleanupService.CleanupResult;

import lombok.RequiredArgsConstructor;

/**
 * Операторские операции над refresh-токенами. Доступ только у роли {@code ROLE_OPERATOR}: запросы идут по основному API и требуют заголовка
 * {@code X-Requested-With} (CSRF-фильтр).
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/operator/tokens")
public class TokenOperatorController {

    private final RefreshTokenCleanupService refreshTokenCleanupService;

    /**
     * Принудительная очистка отозванных токенов тем же механизмом, что и ночное расписание: оценка объёма, затем удаление батчами с
     * адаптивным размером и паузами. Возвращает число удалённых строк и батчей.
     */
    @PostMapping("/cleanup")
    @PreAuthorize("hasRole('OPERATOR')")
    public TokenCleanupResponse cleanupRevokedTokens() {
        CleanupResult result = refreshTokenCleanupService.cleanRevokedTokens();
        return new TokenCleanupResponse(result.deleted(), result.batches());
    }
}
