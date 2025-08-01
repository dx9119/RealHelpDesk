package com.ukhanov.realhelpdesk.domain.ticket.repository;

import com.ukhanov.realhelpdesk.domain.ticket.model.TicketModel;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketPriority;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketStatus;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public class TicketSpecification {

  public static Specification<TicketModel> withFilters(
      List<Long> portalIds,
      String search,
      Instant startDate,
      Instant endDate,
      TicketStatus ticketStatus,
      TicketPriority ticketPriority
  ) {
    return (root, query, cb) -> {
      // JOIN автора и портала для загрузки связанных данных
      root.join("author", JoinType.LEFT);
      root.join("portal", JoinType.LEFT);

      List<Predicate> predicates = new ArrayList<>();

      // Ограничение по порталам
      if (portalIds != null && !portalIds.isEmpty()) {
        predicates.add(root.get("portal").get("id").in(portalIds));
      }

      // Поисковая строка
      if (search != null && !search.isBlank()) {
        String likeSearch = "%" + search.toLowerCase() + "%";

        Predicate titleLike = cb.like(cb.lower(root.get("title")), likeSearch);
        Predicate bodyLike = cb.like(cb.lower(root.get("body")), likeSearch);

        Predicate authorFirst = cb.like(cb.lower(root.get("author").get("firstName")), likeSearch);
        Predicate authorLast = cb.like(cb.lower(root.get("author").get("lastName")), likeSearch);
        Predicate authorMiddle = cb.like(cb.lower(root.get("author").get("middleName")), likeSearch);

        // Объединённое ФИО, с защитой от null
        Expression<String> fullNameConcat = cb.concat(
            cb.concat(
                cb.coalesce(cb.lower(root.get("author").get("lastName")), ""), " "
            ),
            cb.concat(
                cb.coalesce(cb.lower(root.get("author").get("firstName")), ""), " "
            )
        );

        Expression<String> fullNameWithMiddle = cb.concat(
            fullNameConcat,
            cb.coalesce(cb.lower(root.get("author").get("middleName")), "")
        );

        Predicate fullNameMatch = cb.like(fullNameWithMiddle, likeSearch);

        predicates.add(cb.or(
            titleLike,
            bodyLike,
            authorFirst,
            authorLast,
            authorMiddle,
            fullNameMatch
        ));
      }


      if (startDate != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
      }


      if (endDate != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
      }


      if (ticketStatus != null) {
        predicates.add(cb.equal(root.get("ticketStatus"), ticketStatus));
      }


      if (ticketPriority != null) {
        predicates.add(cb.equal(root.get("ticketPriority"), ticketPriority));
      }

      // Убираем дубликаты из-за JOIN FETCH
      query.distinct(true);

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }
}
