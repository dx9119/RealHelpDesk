package com.ukhanov.realhelpdesk.core.mail.service;

import com.ukhanov.realhelpdesk.core.mail.config.EmailProperties;
import com.ukhanov.realhelpdesk.core.mail.exception.EmailAccessDeniedException;
import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;
import com.ukhanov.realhelpdesk.core.security.user.CurrentUserProvider;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.core.security.user.service.UserDomainService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
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

  public EmailDeliveryService(JavaMailSender mailSender,
      UserDomainService userDomainService,
      EmailProperties emailProperties, EmailPolicyService emailPolicyService,
      CurrentUserProvider currentUserProvider) {
    this.mailSender = mailSender;
    this.userDomainService = userDomainService;
    this.emailProperties = emailProperties;
    this.emailPolicyService = emailPolicyService;
    this.currentUserProvider = currentUserProvider;
  }

  public void sendEmail(String recipient, String subject, String text, NotificationEvent sourceEvent) throws MessagingException, EmailAccessDeniedException {
    logger.info("Attempting to send email to recipient: '{}'", recipient);
    logger.info("Recipient string length: {}", recipient != null ? recipient.length() : "null");
    if (recipient != null) {
      logger.info("Recipient string bytes (UTF-8): {}", java.util.Arrays.toString(recipient.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    if(emailPolicyService.isStopList(recipient, sourceEvent)){
      logger.info("skip delivery to {}", recipient);
      return;
    }
    MimeMessage mimeMessage = mailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");

    helper.setTo(recipient);
    helper.setSubject(subject);
    helper.setText(text, false); // false = plain text

    mimeMessage.setHeader("MIME-Version", "1.0");
    mimeMessage.setHeader("Content-Type", "text/plain; charset=UTF-8");
    mimeMessage.setHeader("Content-Transfer-Encoding", "7bit");

    mailSender.send(mimeMessage);
    logger.info("Email sent to recipient: '{}'", recipient);

  }

  public void sendAdminNotification(String subject, String text, NotificationEvent sourceEvent)
      throws MessagingException, EmailAccessDeniedException {
    sendEmail(emailProperties.getNotify(), subject, text, sourceEvent);
  }

  public void sendUserNotification(String userEmail, String subject, String text, NotificationEvent sourceEvent)
      throws MessagingException, EmailAccessDeniedException {
    sendEmail(userEmail, subject, text, sourceEvent);
  }

  public void confirmEmail(UUID token) throws EmailAccessDeniedException {
    UserModel user = currentUserProvider.getCurrentUserModel();
    if(!user.getVerifyEmailToken().equals(token)) {
      throw new EmailAccessDeniedException("Mail token is invalid");
    }
    user.setEmailVerified(true);
    userDomainService.saveUser(user);
  }


}
