package com.ukhanov.realhelpdesk.domain.ticket.repository;

import com.ukhanov.realhelpdesk.domain.ticket.model.TicketLiveStatus;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketModel;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketPriority;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class TicketSpecification {

    public static Specification<TicketModel> build(
            String search,
            Instant startDate,
            Instant endDate,
            TicketStatus ticketStatus,
            TicketPriority ticketPriority,
            Boolean isMyTickets,
            UUID currentUserId,
            Set<Long> accessiblePortalIds) {

        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Глобальный фильтр: только активные заявки
            predicates.add(criteriaBuilder.equal(root.get("ticketLiveStatus"), TicketLiveStatus.ACTIVE));

            // 1. Фильтр по доступным порталам (самый важный для безопасности)
            if (accessiblePortalIds == null || accessiblePortalIds.isEmpty()) {
                // Если нет доступных порталов, возвращаем предикат, который всегда ложен
                return criteriaBuilder.disjunction(); // or 1=0
            }
            predicates.add(root.get("portal").get("id").in(accessiblePortalIds));


            // 2. Фильтр "Только мои заявки"
            if (Boolean.TRUE.equals(isMyTickets) && currentUserId != null) {
                predicates.add(criteriaBuilder.equal(root.get("author").get("id"), currentUserId));
            }

            // 3. Фильтр по поисковой строке (в названии или теле заявки)
            if (StringUtils.hasText(search)) {
                String likePattern = "%" + search.toLowerCase() + "%";
                Predicate titleLike = criteriaBuilder.like(criteriaBuilder.lower(root.get("title")), likePattern);
                Predicate bodyLike = criteriaBuilder.like(criteriaBuilder.lower(root.get("body")), likePattern);
                predicates.add(criteriaBuilder.or(titleLike, bodyLike));
            }

            // 4. Фильтр по дате создания
            if (startDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }
            if (endDate != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }

            // 5. Фильтр по статусу
            if (ticketStatus != null) {
                predicates.add(criteriaBuilder.equal(root.get("ticketStatus"), ticketStatus));
            }

            // 6. Фильтр по приоритету
            if (ticketPriority != null) {
                predicates.add(criteriaBuilder.equal(root.get("ticketPriority"), ticketPriority));
            }

            // Объединяем все условия через AND
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}