package com.ukhanov.realhelpdesk.core.mail.repository;

import jakarta.persistence.PersistenceException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;
import com.ukhanov.realhelpdesk.core.mail.model.UnsubscribedEmail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@DisplayName("Стоп-лист в БД: UnsubscribedEmailRepository")
class UnsubscribedEmailRepositoryIT {

    private static final String EMAIL = "user@example.com";

    @Autowired
    private UnsubscribedEmailRepository repository;

    @Test
    @DisplayName("Запись сохраняется и находится по адресу")
    void findByEmail_returnsSavedRecord() {
        repository.saveAndFlush(new UnsubscribedEmail(EMAIL, NotificationEvent.NEW_TICKET));

        assertThat(repository.findByEmail(EMAIL)).isPresent();
        assertThat(repository.findByEmail(EMAIL).get().getMuteEvent()).isEqualTo(NotificationEvent.NEW_TICKET);
    }

    @Test
    @DisplayName("Неизвестный адрес — пустой результат")
    void findByEmail_unknownAddress_isEmpty() {
        assertThat(repository.findByEmail("nobody@example.com")).isEmpty();
    }

    @Test
    @DisplayName("Удаление по адресу убирает запись")
    void deleteByEmail_removesRecord() {
        repository.saveAndFlush(new UnsubscribedEmail(EMAIL, NotificationEvent.CHANGE_TICKET));

        repository.deleteByEmail(EMAIL);
        repository.flush();

        assertThat(repository.findByEmail(EMAIL)).isEmpty();
    }

    @Test
    @DisplayName("Дубль адреса запрещён уникальным индексом")
    void duplicateEmail_isRejectedByUniqueConstraint() {
        repository.saveAndFlush(new UnsubscribedEmail(EMAIL, NotificationEvent.NEW_TICKET));

        assertThatThrownBy(() -> repository.saveAndFlush(new UnsubscribedEmail(EMAIL, NotificationEvent.CHANGE_TICKET)))
                .isInstanceOfAny(DataIntegrityViolationException.class, PersistenceException.class);
    }
}
