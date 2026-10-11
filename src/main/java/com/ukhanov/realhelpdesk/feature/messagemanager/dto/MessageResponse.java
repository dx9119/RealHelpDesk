package com.ukhanov.realhelpdesk.feature.messagemanager.dto;

import java.time.Instant;

/**
 * Сообщение заявки. {@code authorId} нужен чату, чтобы отрисовать «мои» сообщения: сравнивать по {@code authorFullName} нельзя
 * (однофамильцы, смена имени), а по нему же строятся аватар и ссылка на профиль.
 */
public record MessageResponse(Long id, String messageText, Long authorId, String authorFullName, Long ticketId, Instant createdAt) {
}
