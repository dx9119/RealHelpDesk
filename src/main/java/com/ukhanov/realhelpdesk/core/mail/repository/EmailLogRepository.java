package com.ukhanov.realhelpdesk.core.mail.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ukhanov.realhelpdesk.core.mail.model.EmailLog;
import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;

public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {

    @Query("""
                SELECT COUNT(e)
                FROM EmailLog e
                WHERE e.recipient = :to
                  AND e.notificationEvent = :event
                  AND e.sentAt BETWEEN :start AND :end
            """)
    long countEmailsByToAndEventBetween(@Param("to") String to, @Param("event") NotificationEvent event,
            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

}
