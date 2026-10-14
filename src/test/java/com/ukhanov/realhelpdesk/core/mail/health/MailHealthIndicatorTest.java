package com.ukhanov.realhelpdesk.core.mail.health;

import jakarta.mail.MessagingException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Health-индикатор SMTP (MailHealthIndicator)")
class MailHealthIndicatorTest {

    @Mock
    private JavaMailSenderImpl mailSender;

    private MailHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        indicator = new MailHealthIndicator(mailSender);
    }

    @Test
    @DisplayName("testConnection прошёл → UP с host и port")
    void smtpReachableIsUp() {
        stubConnectionDetails();

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("host", "smtp.example.com").containsEntry("port", 587);
    }

    @Test
    @DisplayName("Сервер недоступен → DOWN; адрес получателя в детали не попадает")
    void smtpUnreachableIsDown() throws Exception {
        stubConnectionDetails();
        doThrow(new MessagingException("Could not connect to SMTP host")).when(mailSender).testConnection();

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("host", "smtp.example.com");
    }

    @Test
    @DisplayName("Отправитель, отличный от JavaMailSenderImpl → UP с пометкой skipped (проверять нечем)")
    void nonJavaMailSenderIsSkipped() {
        MailHealthIndicator fallback = new MailHealthIndicator(mock(JavaMailSender.class));

        Health health = fallback.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("state", "skipped");
    }

    @Test
    @DisplayName("Результат кэшируется: повторный опрос в течение TTL не перепроверяет SMTP")
    void resultIsCached() throws Exception {
        stubConnectionDetails();

        indicator.health();
        indicator.health();

        verify(mailSender, times(1)).testConnection();
    }

    private void stubConnectionDetails() {
        when(mailSender.getHost()).thenReturn("smtp.example.com");
        when(mailSender.getPort()).thenReturn(587);
    }
}
