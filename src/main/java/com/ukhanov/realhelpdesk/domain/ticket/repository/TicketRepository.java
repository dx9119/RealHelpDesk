package com.ukhanov.realhelpdesk.domain.ticket.repository;

import com.ukhanov.realhelpdesk.domain.ticket.model.TicketModel;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<TicketModel, Long> {

    @EntityGraph(attributePaths = {"assignedUser","author","portal"})
    List<TicketModel> findAllByPortalId(@Param("portalId") Long portalId);


}
