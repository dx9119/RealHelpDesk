package com.ukhanov.realhelpdesk.core.mail.service;

import com.ukhanov.realhelpdesk.core.mail.exception.EmailConfirmationException;
import com.ukhanov.realhelpdesk.core.mail.model.Recipients;
import com.ukhanov.realhelpdesk.core.security.user.CurrentUserProvider;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.core.security.user.service.UserDomainService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class MailService {

  private static final Logger logger = LoggerFactory.getLogger(MailService.class);

  private final JavaMailSender mailSender;
  private final UserDomainService userDomainService;
  private final CurrentUserProvider currentUserProvider;

  @Value("${spring.mail.from}")
  private String fromAddress;

  public MailService(JavaMailSender mailSender, UserDomainService userDomainService,
      CurrentUserProvider currentUserProvider) {
    this.mailSender = mailSender;
    this.userDomainService = userDomainService;
    this.currentUserProvider = currentUserProvider;
  }

  public void sendEmail(String recipient, String subject, String text) throws MessagingException {
    MimeMessage mimeMessage = mailSender.createMimeMessage();

    MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");

    helper.setFrom(fromAddress);
    helper.setTo(recipient);
    helper.setSubject(subject);
    helper.setText(text, false); // false = plain text

    mimeMessage.setHeader("MIME-Version", "1.0");
    mimeMessage.setHeader("Content-Type", "text/plain; charset=UTF-8");
    mimeMessage.setHeader("Content-Transfer-Encoding", "7bit");

    mailSender.send(mimeMessage);
    logger.info("Email sent to '{}' with subject '{}'", Recipients.ADMIN.getEmail(), subject);

  }

  public void sendAdminNotification(String subject, String text) throws MessagingException {
    sendEmail(Recipients.ADMIN.getEmail(), subject, text);
  }

  public void sendUserNotification(String userEmail, String subject, String text) throws MessagingException {
    sendEmail(userEmail, subject, text);
  }

  public void confirmEmail(UUID token) throws EmailConfirmationException {
    UserModel user = currentUserProvider.getCurrentUserModel();

    if(!user.getVerifyEmailToken().equals(token)) {
      throw new EmailConfirmationException("Mail token is invalid");
    }
    user.setEmailVerified(true);
    userDomainService.saveUser(user);
  }

}
