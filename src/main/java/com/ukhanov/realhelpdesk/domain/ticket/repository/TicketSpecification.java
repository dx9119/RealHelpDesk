package com.ukhanov.realhelpdesk.domain.ticket.repository;

import com.ukhanov.realhelpdesk.domain.ticket.model.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public class TicketSpecification {

    // Фильтр: только активные тикеты
    public static Specification<TicketModel> active() {
        return (root, query, cb) ->
                cb.equal(root.get("ticketLiveStatus"), TicketLiveStatus.ACTIVE);
    }

    // Фильтр: тикеты только из доступных порталов
    public static Specification<TicketModel> inPortals(Set<Long> portalIds) {
        return (root, query, cb) ->
                root.get("portal").get("id").in(portalIds);
    }

    // Фильтр: тикеты, созданные текущим пользователем
    public static Specification<TicketModel> onlyMy(UUID userId) {
        return (root, query, cb) ->
                cb.equal(root.get("author").get("id"), userId);
    }

    // Фильтр: поиск по заголовку и телу тикета
    public static Specification<TicketModel> search(String text) {
        if (!StringUtils.hasText(text)) return null;

        return (root, query, cb) -> {
            String pattern = "%" + text.toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("body")), pattern)
            );
        };
    }

    // Фильтр: создан после указанной даты
    public static Specification<TicketModel> createdAfter(Instant start) {
        return (root, query, cb) ->
                cb.greaterThanOrEqualTo(root.get("createdAt"), start);
    }

    // Фильтр: создан до указанной даты
    public static Specification<TicketModel> createdBefore(Instant end) {
        return (root, query, cb) ->
                cb.lessThanOrEqualTo(root.get("createdAt"), end);
    }

    // Фильтр: по статусу тикета
    public static Specification<TicketModel> withStatus(TicketStatus status) {
        return (root, query, cb) ->
                cb.equal(root.get("ticketStatus"), status);
    }

    // Фильтр: по приоритету тикета
    public static Specification<TicketModel> withPriority(TicketPriority priority) {
        return (root, query, cb) ->
                cb.equal(root.get("ticketPriority"), priority);
    }

    // Основной билдер спецификаций: собирает все фильтры в одну спецификацию
    public static Specification<TicketModel> build(
            String search,
            Instant startDate,
            Instant endDate,
            TicketStatus ticketStatus,
            TicketPriority ticketPriority,
            Boolean isMyTickets,
            UUID currentUserId,
            Set<Long> accessiblePortalIds) {

        // Если нет доступных порталов — возвращаем пустой результат
        if (accessiblePortalIds == null || accessiblePortalIds.isEmpty()) {
            return Specification.where(null);
        }

        Specification<TicketModel> spec = Specification.where(active())
                .and(inPortals(accessiblePortalIds));

        if (Boolean.TRUE.equals(isMyTickets)) {
            spec = spec.and(onlyMy(currentUserId));
        }

        if (StringUtils.hasText(search)) {
            spec = spec.and(search(search));
        }

        if (startDate != null) {
            spec = spec.and(createdAfter(startDate));
        }

        if (endDate != null) {
            spec = spec.and(createdBefore(endDate));
        }

        if (ticketStatus != null) {
            spec = spec.and(withStatus(ticketStatus));
        }

        if (ticketPriority != null) {
            spec = spec.and(withPriority(ticketPriority));
        }

        return spec;
    }

}
