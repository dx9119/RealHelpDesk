package com.ukhanov.realhelpdesk.domain.portal.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ukhanov.realhelpdesk.domain.portal.model.PortalTransferRequestModel;
import com.ukhanov.realhelpdesk.domain.portal.model.PortalTransferStatus;

@Repository
public interface PortalTransferRequestRepository extends JpaRepository<PortalTransferRequestModel, Long> {

    Optional<PortalTransferRequestModel> findByPortalIdAndStatus(Long portalId, PortalTransferStatus status);
}
