package com.ukhanov.realhelpdesk.core.mail.service;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ukhanov.realhelpdesk.core.mail.dto.EmailInfoResponse;
import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;
import com.ukhanov.realhelpdesk.core.mail.model.UnsubscribedEmail;
import com.ukhanov.realhelpdesk.core.mail.repository.UnsubscribedEmailRepository;
import com.ukhanov.realhelpdesk.core.security.user.CurrentUserProvider;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Стоп-лист: глушится только выбранное событие")
class EmailPolicyServiceTest {

    private static final String EMAIL = "user@test.local";

    private UnsubscribedEmailRepository repository;
    private CurrentUserProvider currentUserProvider;
    private EmailPolicyService service;

    @BeforeEach
    void setUp() {
        repository = mock(UnsubscribedEmailRepository.class);
        currentUserProvider = mock(CurrentUserProvider.class);
        when(repository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        UserModel currentUser = new UserModel();
        currentUser.setEmail(EMAIL);
        when(currentUserProvider.getCurrentUserModel()).thenReturn(currentUser);

        service = new EmailPolicyService(repository, currentUserProvider);
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

    @Test
    @DisplayName("isStopList(null) — ошибка")
    void isStopList_nullEmail_throwsNpe() {
        assertThatThrownBy(() -> service.isStopList(null, NotificationEvent.NEW_TICKET)).isInstanceOf(NullPointerException.class);
    }

    // ────────────────────────────────────────────────
    // addToStopList
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("Отписка без записи создаёт новую с выбранным уровнем")
    void addToStopList_withoutRecord_savesNewEntry() {
        service.addToStopList(NotificationEvent.NEW_TICKET);

        verify(repository).save(argThat(
                (UnsubscribedEmail entry) -> EMAIL.equals(entry.getEmail()) && entry.getMuteEvent() == NotificationEvent.NEW_TICKET));
    }

    @Test
    @DisplayName("Повторная отписка обновляет уровень у существующей записи")
    void addToStopList_withRecord_updatesExistingEntry() {
        UnsubscribedEmail existing = new UnsubscribedEmail(EMAIL, NotificationEvent.NEW_TICKET);
        when(repository.findByEmail(EMAIL)).thenReturn(Optional.of(existing));

        service.addToStopList(NotificationEvent.CHANGE_TICKET);

        verify(repository).save(same(existing));
        assertThat(existing.getMuteEvent()).isEqualTo(NotificationEvent.CHANGE_TICKET);
    }

    @Test
    @DisplayName("Отписка с null-уровнем — ошибка, ничего не сохраняется")
    void addToStopList_nullLevel_throwsNpe() {
        assertThatThrownBy(() -> service.addToStopList(null)).isInstanceOf(NullPointerException.class);

        verify(repository, never()).save(any());
    }

    // ────────────────────────────────────────────────
    // deleteFromStopList
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("Удаление отписки удаляет запись текущего пользователя")
    void deleteFromStopList_removesCurrentUsersRecord() {
        service.deleteFromStopList(42L);

        verify(repository).deleteByEmail(EMAIL);
    }

    @Test
    @DisplayName("Удаление отписки с null-токеном — ошибка")
    void deleteFromStopList_nullToken_throwsNpe() {
        assertThatThrownBy(() -> service.deleteFromStopList(null)).isInstanceOf(NullPointerException.class);

        verify(repository, never()).deleteByEmail(anyString());
    }

    // ────────────────────────────────────────────────
    // getEmailInfo
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("Информация об отписке без записи — уровень NONE")
    void getEmailInfo_withoutRecord_returnsNone() {
        EmailInfoResponse response = service.getEmailInfo();

        assertThat(response.muteLevel()).isEqualTo(NotificationEvent.NONE);
    }

    @Test
    @DisplayName("Информация об отписке возвращает сохранённый уровень")
    void getEmailInfo_withRecord_returnsMuteLevel() {
        muteLevel(NotificationEvent.NEW_MESSAGE);

        EmailInfoResponse response = service.getEmailInfo();

        assertThat(response.muteLevel()).isEqualTo(NotificationEvent.NEW_MESSAGE);
    }

    private void muteLevel(NotificationEvent level) {
        when(repository.findByEmail(EMAIL)).thenReturn(Optional.of(new UnsubscribedEmail(EMAIL, level)));
    }
}
