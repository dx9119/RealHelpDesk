package com.ukhanov.realhelpdesk.feature.notificationmanager.service;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;
import com.ukhanov.realhelpdesk.domain.notification.model.NotificationModel;
import com.ukhanov.realhelpdesk.domain.notification.model.UserNotificationPreferencesModel;
import com.ukhanov.realhelpdesk.domain.notification.repository.NotificationRepository;
import com.ukhanov.realhelpdesk.domain.notification.repository.UserNotificationPreferencesRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Повторы непрочитанных оповещений (NotificationRepeatService)")
class NotificationRepeatServiceTest {

    private static final Long USER_ID = 42L;
    private static final Long SOURCE_ID = 77L;
    private static final Long SOURCE_GROUP_ID = 50L;
    private static final Long TICKET_ID = 11L;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserNotificationPreferencesRepository preferencesRepository;

    @Mock
    private NotificationPublisher notificationPublisher;

    private NotificationRepeatService service;

    @BeforeEach
    void setUp() {
        service = new NotificationRepeatService(notificationRepository, preferencesRepository, notificationPublisher);
        when(notificationRepository.findRepeatCandidates(anySet(), any(Pageable.class))).thenReturn(List.of());
        // как в JPA: save возвращает ту же строку с присвоенным id
        when(notificationRepository.save(any(NotificationModel.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Кандидатов нет — ни сохранений, ни пробуждения")
    void noCandidates_noop() {
        service.repeatDueNotifications();

        verify(notificationRepository, never()).save(any(NotificationModel.class));
        verify(notificationPublisher, never()).wakeAfterCommit(anySet());
    }

    @Test
    @DisplayName("Без строки настроек: источник старше 30 минут → напоминание с группой источника, ожидающих будят")
    void dueWithoutPreferences_createsReminderAndWakes() {
        NotificationModel source = source(Instant.now().minusSeconds(31 * 60));
        when(notificationRepository.findRepeatCandidates(anySet(), any(Pageable.class))).thenReturn(List.of(source));

        service.repeatDueNotifications();

        ArgumentCaptor<NotificationModel> captor = ArgumentCaptor.forClass(NotificationModel.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        NotificationModel reminder = captor.getValue();
        assertThat(reminder.getRecipientId()).isEqualTo(USER_ID);
        assertThat(reminder.getEvent()).isEqualTo(NotificationEvent.NEW_TICKET);
        assertThat(reminder.getTicketId()).isEqualTo(TICKET_ID);
        assertThat(reminder.getTitle()).isEqualTo("Проблема");
        assertThat(reminder.getGroupId()).isEqualTo(SOURCE_ID);
        assertThat(reminder.isRead()).isFalse();
        verify(notificationPublisher).wakeAfterCommit(List.of(USER_ID));
    }

    @Test
    @DisplayName("Напоминание наследует group_id, если источник уже копия")
    void reminderInheritsSourceGroup() {
        NotificationModel source = source(Instant.now().minusSeconds(31 * 60));
        doReturn(SOURCE_GROUP_ID).when(source).getGroupId();
        when(notificationRepository.findRepeatCandidates(anySet(), any(Pageable.class))).thenReturn(List.of(source));

        service.repeatDueNotifications();

        ArgumentCaptor<NotificationModel> captor = ArgumentCaptor.forClass(NotificationModel.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getGroupId()).isEqualTo(SOURCE_GROUP_ID);
    }

    @Test
    @DisplayName("Интервал не истёк — напоминания нет")
    void notYetDue_noReminder() {
        NotificationModel source = source(Instant.now().minusSeconds(5 * 60));
        when(notificationRepository.findRepeatCandidates(anySet(), any(Pageable.class))).thenReturn(List.of(source));

        service.repeatDueNotifications();

        verify(notificationRepository, never()).save(any(NotificationModel.class));
        verify(notificationPublisher, never()).wakeAfterCommit(anySet());
    }

    @Test
    @DisplayName("Повтор выключен в настройках — напоминания нет")
    void repeatDisabled_noReminder() {
        NotificationModel source = source(Instant.now().minusSeconds(31 * 60));
        UserNotificationPreferencesModel preferences = preferences(true, 30, Set.of(NotificationEvent.NEW_TICKET));
        preferences.setRepeatEnabled(false);
        when(notificationRepository.findRepeatCandidates(anySet(), any(Pageable.class))).thenReturn(List.of(source));
        when(preferencesRepository.findByUserIdIn(anySet())).thenReturn(List.of(preferences));

        service.repeatDueNotifications();

        verify(notificationRepository, never()).save(any(NotificationModel.class));
        verify(notificationPublisher, never()).wakeAfterCommit(anySet());
    }

    @Test
    @DisplayName("Событие выключено в настройках — напоминания нет")
    void eventDisabled_noReminder() {
        NotificationModel source = source(Instant.now().minusSeconds(31 * 60));
        UserNotificationPreferencesModel preferences = preferences(true, 30, Set.of(NotificationEvent.CHANGE_TICKET));
        when(notificationRepository.findRepeatCandidates(anySet(), any(Pageable.class))).thenReturn(List.of(source));
        when(preferencesRepository.findByUserIdIn(anySet())).thenReturn(List.of(preferences));

        service.repeatDueNotifications();

        verify(notificationRepository, never()).save(any(NotificationModel.class));
        verify(notificationPublisher, never()).wakeAfterCommit(anySet());
    }

    @Test
    @DisplayName("Пользовательский интервал учитывается вместо 30 минут по умолчанию")
    void customInterval_respected() {
        NotificationModel source = source(Instant.now().minusSeconds(2 * 60));
        UserNotificationPreferencesModel everyMinute = preferences(true, 1, Set.of(NotificationEvent.NEW_TICKET));
        when(notificationRepository.findRepeatCandidates(anySet(), any(Pageable.class))).thenReturn(List.of(source));
        when(preferencesRepository.findByUserIdIn(anySet())).thenReturn(List.of(everyMinute));

        service.repeatDueNotifications();

        verify(notificationRepository, times(1)).save(any(NotificationModel.class));
        verify(notificationPublisher).wakeAfterCommit(List.of(USER_ID));
    }

    private NotificationModel source(Instant createdAt) {
        NotificationModel source = spy(new NotificationModel(USER_ID, NotificationEvent.NEW_TICKET, TICKET_ID, 2L, "Проблема"));
        doReturn(SOURCE_ID).when(source).getId();
        source.setCreatedAt(createdAt);
        return source;
    }

    private UserNotificationPreferencesModel preferences(boolean repeatEnabled, int intervalMinutes, Set<NotificationEvent> events) {
        UserNotificationPreferencesModel preferences = new UserNotificationPreferencesModel(USER_ID, events);
        preferences.setRepeatEnabled(repeatEnabled);
        preferences.setRepeatIntervalMinutes(intervalMinutes);
        return preferences;
    }
}
