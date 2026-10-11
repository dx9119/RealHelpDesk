package com.ukhanov.realhelpdesk.core.mail.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import jakarta.transaction.Transactional;

import org.springframework.stereotype.Service;

import com.ukhanov.realhelpdesk.core.mail.model.EmailLog;
import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;
import com.ukhanov.realhelpdesk.core.mail.repository.EmailLogRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class EmailLogService {

    private final EmailLogRepository emailLogRepository;

    public long countEmailsSentToByEventInWindow(String email, NotificationEvent event, Duration window) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = now.minus(window);
        return emailLogRepository.countEmailsByToAndEventBetween(email, event, from, now);
    }

    @Transactional
    public void add(EmailLog emailLog) {
        Objects.requireNonNull(emailLog, "emailLog не может быть пустым");
        emailLogRepository.save(emailLog);
    }

    /** Пачка журнала одной записью после рассылки вместо save на каждое письмо. */
    @Transactional
    public void addAll(List<EmailLog> emailLogs) {
        Objects.requireNonNull(emailLogs, "emailLogs не может быть null");
        if (!emailLogs.isEmpty()) {
            emailLogRepository.saveAll(emailLogs);
        }
    }
}
