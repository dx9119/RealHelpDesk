package com.ukhanov.RealHelpDesk.domain.ticket.repository;

import com.ukhanov.RealHelpDesk.domain.ticket.model.TicketModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<TicketModel, Long> {

    @Query("SELECT t FROM TicketModel t JOIN FETCH t.portal WHERE t.portal.id = :portalId")
    List<TicketModel> findAllWithPortalByPortalId(@Param("portalId") Long portalId);


}
