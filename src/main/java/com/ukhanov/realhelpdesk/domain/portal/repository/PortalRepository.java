package com.ukhanov.realhelpdesk.domain.portal.repository;


import com.ukhanov.realhelpdesk.domain.portal.model.PortalModel;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PortalRepository extends JpaRepository<PortalModel, Long> {

    List<PortalModel> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    Page<PortalModel> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId, Pageable pageable);

    boolean existsByName(String name);

}