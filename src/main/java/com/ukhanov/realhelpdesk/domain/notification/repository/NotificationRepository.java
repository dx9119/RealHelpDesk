package com.ukhanov.realhelpdesk.domain.notification.repository;

import java.util.List;
import java.util.Optional;

import jakarta.transaction.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ukhanov.realhelpdesk.domain.notification.model.NotificationModel;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationModel, Long> {

    Page<NotificationModel> findByRecipientId(Long recipientId, Pageable pageable);

    Page<NotificationModel> findByRecipientIdAndReadFalse(Long recipientId, Pageable pageable);

    /** Новые для long polling: строго больше курсора, по возрастанию id — клиент догоняет пачками. */
    List<NotificationModel> findByRecipientIdAndIdGreaterThanOrderByIdAsc(Long recipientId, Long afterId, Pageable pageable);

    Optional<NotificationModel> findByIdAndRecipientId(Long id, Long recipientId);

    long countByRecipientIdAndReadFalse(Long recipientId);

    @Modifying
    @Transactional
    @Query("update NotificationModel n set n.read = true where n.recipientId = :recipientId and n.read = false")
    int markAllReadByRecipientId(@Param("recipientId") Long recipientId);
}
