package com.ukhanov.RealHelpDesk.domain.portal.repository;


import com.ukhanov.RealHelpDesk.domain.portal.model.PortalModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PortalRepository extends JpaRepository<PortalModel, Long> {

    List<PortalModel> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    boolean existsByName(String name);

}