package com.ukhanov.realhelpdesk.core.mail.service;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;
import com.ukhanov.realhelpdesk.core.mail.model.UnsubscribedEmail;
import com.ukhanov.realhelpdesk.core.mail.repository.UnsubscribedEmailRepository;
import com.ukhanov.realhelpdesk.core.security.user.CurrentUserProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Стоп-лист: глушится только выбранное событие")
class EmailPolicyServiceTest {

    private static final String EMAIL = "user@test.local";

    private UnsubscribedEmailRepository repository;
    private EmailPolicyService service;

    @BeforeEach
    void setUp() {
        repository = mock(UnsubscribedEmailRepository.class);
        when(repository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        service = new EmailPolicyService(repository, new CurrentUserProvider(null));
    }

    @Test
    @DisplayName("Нет записи в стоп-листе — письмо уходит")
    void noRecord_notBlocked() {
        assertThat(service.isStopList(EMAIL, NotificationEvent.RECOVERY_PASSWORD)).isFalse();
        assertThat(service.isStopList(EMAIL, NotificationEvent.NEW_TICKET)).isFalse();
    }

    @Test
    @DisplayName("Уровень NONE — письмо уходит")
    void none_notBlocked() {
        muteLevel(NotificationEvent.NONE);

        assertThat(service.isStopList(EMAIL, NotificationEvent.NEW_TICKET)).isFalse();
        assertThat(service.isStopList(EMAIL, NotificationEvent.CHANGE_TICKET)).isFalse();
    }

    @Test
    @DisplayName("Отписка от CHANGE_TICKET не блокирует восстановление пароля")
    void changeTicketMute_doesNotBlockRecoveryPassword() {
        muteLevel(NotificationEvent.CHANGE_TICKET);

        assertThat(service.isStopList(EMAIL, NotificationEvent.RECOVERY_PASSWORD)).isFalse();
        assertThat(service.isStopList(EMAIL, NotificationEvent.NEW_SYSTEM_MESSAGE)).isFalse();
        assertThat(service.isStopList(EMAIL, NotificationEvent.CHANGE_TICKET)).isTrue();
    }

    @Test
    @DisplayName("Уровень NEW_TICKET_OR_MESSAGE глушит только новые заявки и сообщения")
    void newTicketOrMessage_blocksOnlyNewTicketAndMessage() {
        muteLevel(NotificationEvent.NEW_TICKET_OR_MESSAGE);

        assertThat(service.isStopList(EMAIL, NotificationEvent.NEW_TICKET)).isTrue();
        assertThat(service.isStopList(EMAIL, NotificationEvent.NEW_MESSAGE)).isTrue();
        assertThat(service.isStopList(EMAIL, NotificationEvent.RECOVERY_PASSWORD)).isFalse();
        assertThat(service.isStopList(EMAIL, NotificationEvent.CHANGE_TICKET)).isFalse();
    }

    @Test
    @DisplayName("Глушится только совпавшее событие")
    void exactEventMatch_only() {
        muteLevel(NotificationEvent.NEW_TICKET);

        assertThat(service.isStopList(EMAIL, NotificationEvent.NEW_TICKET)).isTrue();
        assertThat(service.isStopList(EMAIL, NotificationEvent.NEW_MESSAGE)).isFalse();
        assertThat(service.isStopList(EMAIL, NotificationEvent.CHANGE_TICKET)).isFalse();
        assertThat(service.isStopList(EMAIL, NotificationEvent.RECOVERY_PASSWORD)).isFalse();
    }

    private void muteLevel(NotificationEvent level) {
        when(repository.findByEmail(EMAIL)).thenReturn(Optional.of(new UnsubscribedEmail(EMAIL, level)));
    }
}
