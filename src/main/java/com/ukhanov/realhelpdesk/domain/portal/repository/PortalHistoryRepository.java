package com.ukhanov.realhelpdesk.domain.portal.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ukhanov.realhelpdesk.domain.portal.model.PortalHistoryModel;

@Repository
public interface PortalHistoryRepository extends JpaRepository<PortalHistoryModel, Long> {

    // Сортировку задаёт Pageable (единый стиль page/size/sortBy/order), а не имя метода:
    // жёсткий OrderByCreatedAtDesc не давал клиенту выбрать порядок и расходился с остальными списками.
    Page<PortalHistoryModel> findByPortalId(Long portalId, Pageable pageable);
}
