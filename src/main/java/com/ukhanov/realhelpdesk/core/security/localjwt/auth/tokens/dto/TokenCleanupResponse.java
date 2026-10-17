package com.ukhanov.realhelpdesk.core.security.localjwt.auth.tokens.dto;

/**
 * Результат принудительной очистки отозванных refresh-токенов: сколько строк удалено и за сколько батчей.
 */
public record TokenCleanupResponse(long revokedDeleted, int batches) {
}
