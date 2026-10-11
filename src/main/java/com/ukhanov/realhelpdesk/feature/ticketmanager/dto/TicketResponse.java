package com.ukhanov.realhelpdesk.feature.ticketmanager.dto;

import java.time.Instant;

import com.ukhanov.realhelpdesk.domain.ticket.model.TicketAccessStatus;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketPriority;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketStatus;

/**
 * Единое представление заявки для всех эндпоинтов: список портала, деталь, «мои заявки» и поиск.
 *
 * <p>
 * Раньше список/деталь отдавали {@code TicketResponseOld}, а поиск — урезанный {@code TicketResponse} без тела и статусов, поэтому клиент
 * держал два типа и две ветки рендера для одного списка заявок.
 *
 * <p>
 * {@code authorId} нужен UI, чтобы отличить свои заявки от чужих: сравнивать по {@code authorFullName} нельзя (однофамильцы, смена имени).
 *
 * <p>
 * {@code version} — колонка {@code @Version}: конфликт при параллельном изменении приходит как 409, а наличие версии в ответе позволяет
 * клиенту заметить устаревшие данные до отправки.
 */
public record TicketResponse(Long id, String title, String body, Long authorId, String authorFullName, String portalName, Long portalId,
        TicketPriority ticketPriority, TicketStatus ticketStatus, TicketAccessStatus ticketAccessStatus, Instant createdAt,
        Integer version) {
}
