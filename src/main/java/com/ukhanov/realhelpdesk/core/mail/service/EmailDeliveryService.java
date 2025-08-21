package com.ukhanov.realhelpdesk.core.mail.service;

import com.ukhanov.realhelpdesk.core.mail.config.EmailProperties;
import com.ukhanov.realhelpdesk.core.mail.exception.EmailAccessDeniedException;
import com.ukhanov.realhelpdesk.core.mail.model.EmailLog;
import com.ukhanov.realhelpdesk.core.mail.model.EmailTemplates;
import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;
import com.ukhanov.realhelpdesk.core.mail.repository.EmailLogRepository;
import com.ukhanov.realhelpdesk.core.security.user.CurrentUserProvider;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.core.security.user.service.UserDomainService;
import com.ukhanov.realhelpdesk.domain.portal.model.PortalModel;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailDeliveryService {

  private static final Logger logger = LoggerFactory.getLogger(EmailDeliveryService.class);

  private final JavaMailSender mailSender;
  private final UserDomainService userDomainService;
  private final EmailProperties emailProperties;
  private final EmailPolicyService emailPolicyService;
  private final CurrentUserProvider currentUserProvider;
  private final EmailLogService emailLogService;


  public EmailDeliveryService(JavaMailSender mailSender,
                              UserDomainService userDomainService,
                              EmailProperties emailProperties, EmailPolicyService emailPolicyService,
                              CurrentUserProvider currentUserProvider, EmailLogService emailLogService, EmailLogRepository emailLogRepository) {
    this.mailSender = mailSender;
    this.userDomainService = userDomainService;
    this.emailProperties = emailProperties;
    this.emailPolicyService = emailPolicyService;
    this.currentUserProvider = currentUserProvider;
      this.emailLogService = emailLogService;
  }

  public void sendEmail(String recipient, String subject, String text, NotificationEvent sourceEvent) throws MessagingException, EmailAccessDeniedException, UnsupportedEncodingException {

    logger.info("Начал попытку отправки письма: '{},{},{}'", recipient, subject, sourceEvent);

    emailLogService.add(new EmailLog(subject,emailProperties.getFrom(),recipient,sourceEvent));

    if(sourceEvent == NotificationEvent.RECOVERY_PASSWORD){
      emailLimiter(recipient);
    }

    if(emailPolicyService.isStopList(recipient, sourceEvent)){
      logger.debug("Пропускаем email из столп листа {}", recipient);
      return;
    }

    MimeMessage mimeMessage = mailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");

    helper.setFrom(emailProperties.getFrom(), "Оповещения Заявус");
    helper.setTo(recipient);
    helper.setSubject(subject);
    helper.setText(text, false); // false = plain text

    mimeMessage.setHeader("MIME-Version", "1.0");
    mimeMessage.setHeader("Content-Type", "text/plain; charset=UTF-8");
    mimeMessage.setHeader("Content-Transfer-Encoding", "7bit");

    mailSender.send(mimeMessage);
    logger.info("Письмо ушло получателю: '{}'", recipient);

  }

  public void emailLimiter (String recipient){
    long count = emailLogService.countEmailsSentToByEventInLast24Hours(
            recipient,
            NotificationEvent.RECOVERY_PASSWORD
    );

    if (count > 3){
      throw new EmailAccessDeniedException("Исчерпан лимит на количество запросов восстановления, обновление лимита через 24 часа");
    }

  }

  public void sendAdminNotification(String subject, String text, NotificationEvent sourceEvent)
          throws MessagingException, EmailAccessDeniedException, UnsupportedEncodingException {
    sendEmail(emailProperties.getNotify(), subject, text, sourceEvent);
  }

  public void sendUserNotification(String userEmail, String subject, String text, NotificationEvent sourceEvent)
          throws MessagingException, EmailAccessDeniedException, UnsupportedEncodingException {
    sendEmail(userEmail, subject, text, sourceEvent);
  }

  public void initNotifyPortalUsers(PortalModel portal, String subject, String message, NotificationEvent sourceEvent)
          throws MessagingException, UnsupportedEncodingException {
    Set<UUID> portalUsers = new HashSet<>(Arrays.asList(
            portal.getOwner().getId()
    ));
    // Проверяем, что есть внешние пользователи
    if (portal.getAllowedUserIds() != null) {
      portalUsers.addAll(portal.getAllowedUserIds());
    }

    sendPortalUsersNotification(
        portalUsers,
        subject,
        message,
        sourceEvent
    );

  }

  public void sendPortalUsersNotification(Set<UUID> userIds, String subject, String text, NotificationEvent sourceEvent)
          throws MessagingException, EmailAccessDeniedException, UnsupportedEncodingException {

    for (UUID userId : userIds) {
      UserModel user = userDomainService.getUserById(userId);
      sendEmail(user.getEmail(), subject, text, sourceEvent);
    }

  }

  public void confirmEmail(UUID token) throws EmailAccessDeniedException {
    UserModel user = currentUserProvider.getCurrentUserModel();
    if(!user.getVerifyEmailToken().equals(token)) {
      throw new EmailAccessDeniedException("Код подтверждения права владения почтой не верный");
    }
    user.setEmailVerified(true);
    userDomainService.saveUser(user);
  }

  public void sendConfirmCode () throws MessagingException, UnsupportedEncodingException {
    UserModel user = currentUserProvider.getCurrentUserModel();

    sendUserNotification(
            user.getEmail(),
            EmailTemplates.registrationCodeSubject(),
            EmailTemplates.registrationCodeBody(user.getVerifyEmailToken().toString()),
            NotificationEvent.NEW_SYSTEM_MESSAGE
    );

  }


}
