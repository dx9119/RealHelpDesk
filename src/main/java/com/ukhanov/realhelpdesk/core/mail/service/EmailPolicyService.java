package com.ukhanov.realhelpdesk.core.mail.service;

import com.ukhanov.realhelpdesk.core.mail.dto.EmailInfoResponse;
import com.ukhanov.realhelpdesk.core.mail.model.UnsubscribedEmail;
import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;
import com.ukhanov.realhelpdesk.core.mail.repository.UnsubscribedEmailRepository;
import com.ukhanov.realhelpdesk.core.security.user.CurrentUserProvider;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EmailPolicyService {
  private final UnsubscribedEmailRepository repository;
  private final CurrentUserProvider currentUserProvider;

  private static final Logger logger = LoggerFactory.getLogger(EmailPolicyService.class);

  public EmailPolicyService(UnsubscribedEmailRepository repository, CurrentUserProvider currentUserProvider,
      CurrentUserProvider currentUserProvider1) {
    this.repository = repository;
    this.currentUserProvider = currentUserProvider1;
  }

  public boolean isStopList(String email, NotificationEvent sourceEvent) {
    Objects.requireNonNull(email, "Email is null");

    NotificationEvent stopListEmail = repository.findByEmail(email)
        .map(UnsubscribedEmail::getMuteEvent)
        .orElse(null);

    // источник события откуда отправляется письмо, если событие совпадает с событием которое в стоп листе, то письмо не отправляется
    if (stopListEmail == sourceEvent) {
      logger.debug("Email is blocked for notifications about: " + stopListEmail);
      return true;
    }
    // если в стоп листе стоит событие всех новых заявок и сообщений, то письмо не отправляется
    if (stopListEmail == NotificationEvent.NEW_TICKET_OR_MESSAGE){
      logger.debug("Email is blocked for notifications about: " + stopListEmail);
      return true;
    }
    // нет ограничений на отправку писем
    if (stopListEmail == NotificationEvent.NONE) {
      return false;
    }
    return false;
  }

  public void deleteFromStopList(UUID token) {
    Objects.requireNonNull(token, "Token is null");

    UserModel user = currentUserProvider.getCurrentUserModel();

    repository.deleteByEmail(user.getEmail());
  }

  public void addToStopList(NotificationEvent level) {
    Objects.requireNonNull(level, "Level is null");

    UserModel user = currentUserProvider.getCurrentUserModel();

    Optional<UnsubscribedEmail> existing = repository.findByEmail(user.getEmail());
    if (existing.isPresent()) {
      UnsubscribedEmail record = existing.get();
      record.setMuteEvent(level);
      record.setInStopListAt(Instant.now());
      repository.save(record);
    } else {
      UnsubscribedEmail newEntry = new UnsubscribedEmail(user.getEmail(), level);
      newEntry.setInStopListAt(Instant.now());
      repository.save(newEntry);
    }
  }

  public EmailInfoResponse getEmailInfo() {
    UserModel user = currentUserProvider.getCurrentUserModel();
    NotificationEvent level = repository.findByEmail(user.getEmail())
        .map(UnsubscribedEmail::getMuteEvent)
        .orElse(NotificationEvent.NONE);

    EmailInfoResponse response = new EmailInfoResponse();
    response.setMuteLevel(level);
    return response;
  }



}
