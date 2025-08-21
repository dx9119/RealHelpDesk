package com.ukhanov.realhelpdesk.core.mail.service;

import com.ukhanov.realhelpdesk.core.mail.model.EmailLog;
import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;
import com.ukhanov.realhelpdesk.core.mail.repository.EmailLogRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class EmailLogService {

    private final EmailLogRepository emailLogRepository;

    public EmailLogService(EmailLogRepository emailLogRepository) {
        this.emailLogRepository = emailLogRepository;
    }


    public long countEmailsSentToByEventInLast24Hours(String email, NotificationEvent event) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = now.minusHours(24);
        return emailLogRepository.countEmailsByToAndEventBetween(email, event, from, now);
    }

    @Transactional
    public void add(EmailLog emailLog){
        Objects.requireNonNull(emailLog,"emailLog не может быть пустым");
        emailLogRepository.save(emailLog);
    }
}
