package com.ukhanov.realhelpdesk;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.ukhanov.realhelpdesk.core.security.auth.tokens.model.RefreshTokenModel;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.domain.message.model.MessageModel;
import com.ukhanov.realhelpdesk.domain.portal.model.PortalModel;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketModel;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@DisplayName("Выставление createdAt у сущностей (@PrePersist)")
class CreatedAtPrePersistTest {

    @Autowired
    private EntityManager em;

    private static final Instant PRESET = Instant.parse("2020-01-15T10:30:00Z");
    private static final Duration TOLERANCE = Duration.ofSeconds(1);

    private Instant startedAt;
    private int userSequence = 0;

    @BeforeEach
    void markStart() {
        startedAt = Instant.now();
    }

    // ────────────────────────────────────────────────
    // TicketModel
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("TicketModel → дата не задана → createdAt = текущее время")
    void ticket_setsCreatedAtWhenAbsent() {
        UserModel author = user();
        em.persist(author);
        PortalModel portal = portal(author);
        em.persist(portal);

        TicketModel ticket = ticket(author, portal);
        em.persist(ticket);

        assertCreatedAtIsNow(reload(ticket, TicketModel.class).getCreatedAt());
    }

    @Test
    @DisplayName("TicketModel → дата задана → createdAt не перезаписывается")
    void ticket_keepsPresetCreatedAt() {
        UserModel author = user();
        em.persist(author);
        PortalModel portal = portal(author);
        em.persist(portal);

        TicketModel ticket = ticket(author, portal);
        ticket.setCreatedAt(PRESET);
        em.persist(ticket);

        assertThat(reload(ticket, TicketModel.class).getCreatedAt()).isEqualTo(PRESET);
    }

    // ────────────────────────────────────────────────
    // PortalModel
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("PortalModel → дата не задана → createdAt = текущее время")
    void portal_setsCreatedAtWhenAbsent() {
        UserModel owner = user();
        em.persist(owner);

        PortalModel portal = portal(owner);
        em.persist(portal);

        assertCreatedAtIsNow(reload(portal, PortalModel.class).getCreatedAt());
    }

    @Test
    @DisplayName("PortalModel → дата задана → createdAt не перезаписывается")
    void portal_keepsPresetCreatedAt() {
        UserModel owner = user();
        em.persist(owner);

        PortalModel portal = portal(owner);
        portal.setCreatedAt(PRESET);
        em.persist(portal);

        assertThat(reload(portal, PortalModel.class).getCreatedAt()).isEqualTo(PRESET);
    }

    // ────────────────────────────────────────────────
    // MessageModel
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("MessageModel → дата не задана → createdAt = текущее время")
    void message_setsCreatedAtWhenAbsent() {
        MessageModel message = persistedMessage();

        assertCreatedAtIsNow(reload(message, MessageModel.class).getCreatedAt());
    }

    @Test
    @DisplayName("MessageModel → дата задана → createdAt не перезаписывается")
    void message_keepsPresetCreatedAt() {
        UserModel author = user();
        em.persist(author);
        PortalModel portal = portal(author);
        em.persist(portal);
        TicketModel ticket = ticket(author, portal);
        em.persist(ticket);

        MessageModel message = message(ticket, author);
        message.setCreatedAt(PRESET);
        em.persist(message);

        assertThat(reload(message, MessageModel.class).getCreatedAt()).isEqualTo(PRESET);
    }

    // ────────────────────────────────────────────────
    // UserModel
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("UserModel → дата не задана → createdAt = текущее время")
    void user_setsCreatedAtWhenAbsent() {
        UserModel user = user();
        em.persist(user);

        assertCreatedAtIsNow(reload(user, UserModel.class).getCreatedAt());
    }

    @Test
    @DisplayName("UserModel → дата задана → createdAt не перезаписывается")
    void user_keepsPresetCreatedAt() {
        UserModel user = user();
        setCreatedAtDirectly(user, PRESET);
        em.persist(user);

        assertThat(reload(user, UserModel.class).getCreatedAt()).isEqualTo(PRESET);
    }

    // ────────────────────────────────────────────────
    // RefreshTokenModel
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("RefreshTokenModel → дата не задана → createdAt = текущее время")
    void refreshToken_setsCreatedAtWhenAbsent() {
        UserModel owner = user();
        em.persist(owner);

        RefreshTokenModel token = token(owner);
        em.persist(token);

        assertCreatedAtIsNow(reload(token, RefreshTokenModel.class).getCreatedAt());
    }

    @Test
    @DisplayName("RefreshTokenModel → дата задана → createdAt не перезаписывается")
    void refreshToken_keepsPresetCreatedAt() {
        UserModel owner = user();
        em.persist(owner);

        RefreshTokenModel token = token(owner);
        setCreatedAtDirectly(token, PRESET);
        em.persist(token);

        assertThat(reload(token, RefreshTokenModel.class).getCreatedAt()).isEqualTo(PRESET);
    }

    // ────────────────────────────────────────────────
    // Вспомогательные методы
    // ────────────────────────────────────────────────

    private void assertCreatedAtIsNow(Instant createdAt) {
        assertThat(createdAt).isNotNull().isBetween(startedAt.minus(TOLERANCE), Instant.now().plus(TOLERANCE));
    }

    /**
     * Читает сущность из БД после flush+clear: проверяем то, что реально ушло в базу, а не значение из первого уровня кэша.
     */
    private <T> T reload(T entity, Class<T> type) {
        em.flush();
        Object id = em.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(entity);
        em.clear();
        return em.find(type, id);
    }

    /**
     * У UserModel и RefreshTokenModel публичного сеттера createdAt нет — поле иммутабельно снаружи и заполняется только @PrePersist. В
     * тесте задаём его рефлексией.
     */
    private void setCreatedAtDirectly(Object entity, Instant createdAt) {
        try {
            Field field = entity.getClass().getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(entity, createdAt);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Не удалось задать createdAt у " + entity.getClass().getSimpleName(), e);
        }
    }

    private UserModel user() {
        userSequence++;
        UserModel user = new UserModel();
        user.setEmail("user" + userSequence + "@example.com");
        user.setFirstName("Иван");
        user.setLastName("Петров");
        user.setPasswordHash("hash");
        return user;
    }

    private PortalModel portal(UserModel owner) {
        PortalModel portal = new PortalModel();
        portal.setName("Портал " + userSequence);
        portal.setOwner(owner);
        return portal;
    }

    private TicketModel ticket(UserModel author, PortalModel portal) {
        TicketModel ticket = new TicketModel();
        ticket.setTitle("Заявка");
        ticket.setBody("Описание заявки");
        ticket.setAuthor(author);
        ticket.setPortal(portal);
        return ticket;
    }

    private MessageModel message(TicketModel ticket, UserModel author) {
        MessageModel message = new MessageModel();
        message.setTicket(ticket);
        message.setAuthor(author);
        message.setMessageText("Текст сообщения");
        return message;
    }

    private RefreshTokenModel token(UserModel user) {
        RefreshTokenModel token = new RefreshTokenModel();
        token.setToken("refresh-token-value");
        token.setUser(user);
        return token;
    }

    private MessageModel persistedMessage() {
        UserModel author = user();
        em.persist(author);
        PortalModel portal = portal(author);
        em.persist(portal);
        TicketModel ticket = ticket(author, portal);
        em.persist(ticket);

        MessageModel message = message(ticket, author);
        em.persist(message);
        return message;
    }
}
