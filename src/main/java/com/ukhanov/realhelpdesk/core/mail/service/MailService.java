package com.ukhanov.realhelpdesk.core.mail.service;

import com.ukhanov.realhelpdesk.core.mail.model.Recipients;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
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

  @Value("${spring.mail.from}")
  private String fromAddress;

  public MailService(JavaMailSender mailSender) {
    this.mailSender = mailSender;
  }

  public void sendMail(String subject, String text) {
    MimeMessage mimeMessage = mailSender.createMimeMessage();

    try {
      MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");

      helper.setFrom(fromAddress);
      helper.setTo(Recipients.ADMIN.getEmail());
      helper.setSubject(subject);
      helper.setText(text, false); // false = plain text

      mimeMessage.setHeader("MIME-Version", "1.0");
      mimeMessage.setHeader("Content-Type", "text/plain; charset=UTF-8");
      mimeMessage.setHeader("Content-Transfer-Encoding", "7bit");

      mailSender.send(mimeMessage);
      logger.info("Email sent to '{}' with subject '{}'", Recipients.ADMIN.getEmail(), subject);

    } catch (MessagingException e) {
      logger.error("Failed to send email: {}", e.getMessage(), e);
    }
  }
}
