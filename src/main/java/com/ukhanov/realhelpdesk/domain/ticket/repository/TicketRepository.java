package com.ukhanov.realhelpdesk.domain.ticket.repository;

import com.ukhanov.realhelpdesk.domain.ticket.model.TicketModel;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<TicketModel, Long> {

    @EntityGraph(attributePaths = {"assignedUser","author","portal"})
    List<TicketModel> findAllByPortalId(@Param("portalId") Long portalId);

    @EntityGraph(attributePaths = {"assignedUser", "author", "portal"})
    Page<TicketModel> findAllByPortalId(@Param("portalId") Long portalId, Pageable pageable);

    @EntityGraph(attributePaths = {"assignedUser", "author", "portal"})
    Page<TicketModel> findAllByAuthorId(@Param("authorId") UUID authorId, Pageable pageable);

    @EntityGraph(attributePaths = {"assignedUser", "author", "portal"})
    Optional<TicketModel> findById(Long id);

    @Query("""
    SELECT t.id FROM TicketModel t
    LEFT JOIN MessageModel m ON m.ticket = t
    WHERE t.portal.id = :portalId AND m.id IS NULL
""")
    Set<Long> findTicketIdsWithoutMessagesByPortalId(
        @Param("portalId") Long portalId
    );


}
