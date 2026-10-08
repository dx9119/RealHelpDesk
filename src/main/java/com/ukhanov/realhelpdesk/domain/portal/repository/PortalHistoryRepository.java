package com.ukhanov.realhelpdesk.domain.portal.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ukhanov.realhelpdesk.domain.portal.model.PortalHistoryModel;

@Repository
public interface PortalHistoryRepository extends JpaRepository<PortalHistoryModel, Long> {

    Page<PortalHistoryModel> findByPortalIdOrderByCreatedAtDesc(Long portalId, Pageable pageable);
}
