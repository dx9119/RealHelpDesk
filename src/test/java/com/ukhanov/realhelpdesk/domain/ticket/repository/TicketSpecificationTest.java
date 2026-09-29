package com.ukhanov.realhelpdesk.domain.ticket.repository;

import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.domain.portal.model.PortalModel;
import com.ukhanov.realhelpdesk.domain.ticket.model.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@DisplayName("Спецификации поиска заявок (TicketSpecification)")
class TicketSpecificationTest {

    @Autowired
    private EntityManager em;

    private UserModel alice;
    private UserModel bob;

    private PortalModel portalA;
    private PortalModel portalB;

    private TicketModel t1; // Портал A, ошибка входа, OPEN/HIGH, 2025-02-10, alice
    private TicketModel t2; // Портал A, ошибка оплаты в теле, CLOSED/CRITICAL, 2025-05-20, bob
    private TicketModel t3; // Портал A, отчёты, IN_PROGRESS/LOW, 2024-12-31, alice
    private TicketModel t4; // Портал B, ошибка входа, OPEN/HIGH, 2025-03-01, bob
    private TicketModel t5; // Портал A, ошибка входа, live=DELETE, 2025-06-01, alice

    private static final Instant D1 = Instant.parse("2025-02-10T12:00:00Z");
    private static final Instant D2 = Instant.parse("2025-05-20T12:00:00Z");
    private static final Instant D3 = Instant.parse("2024-12-31T12:00:00Z");
    private static final Instant D4 = Instant.parse("2025-03-01T12:00:00Z");
    private static final Instant D5 = Instant.parse("2025-06-01T12:00:00Z");

    @BeforeEach
    void seed() {
        alice = user("alice@example.com", "Алиса", "Иванова");
        bob = user("bob@example.com", "Борис", "Сидоров");

        portalA = portal("Портал А", alice, false, Set.of());
        portalB = portal("Портал Б", bob, true, Set.of());
        portalA.setAllowedUserIds(Set.of(bob.getId()));

        t1 = ticket("Ошибка входа", "Не работает пароль", alice, portalA,
                TicketStatus.OPEN, TicketPriority.HIGH, TicketLiveStatus.ACTIVE);
        t2 = ticket("Проблема с оплатой", "Карта не проходит, ошибка 402", bob, portalA,
                TicketStatus.CLOSED, TicketPriority.CRITICAL, TicketLiveStatus.ACTIVE);
        t3 = ticket("Вопрос по отчётам", "Как выгрузить статистику", alice, portalA,
                TicketStatus.IN_PROGRESS, TicketPriority.LOW, TicketLiveStatus.ACTIVE);
        t4 = ticket("Ошибка входа", "Не пускает в систему", bob, portalB,
                TicketStatus.OPEN, TicketPriority.HIGH, TicketLiveStatus.ACTIVE);
        t5 = ticket("Ошибка входа (архив)", "Старая заявка", alice, portalA,
                TicketStatus.OPEN, TicketPriority.MEDIUM, TicketLiveStatus.DELETE);

        em.flush();

        // createdAt: updatable=false → менять через dirty-checking нельзя, только bulk-update
        setCreatedAt(t1, D1);
        setCreatedAt(t2, D2);
        setCreatedAt(t3, D3);
        setCreatedAt(t4, D4);
        setCreatedAt(t5, D5);

        em.clear();
    }

    private void setCreatedAt(TicketModel ticket, Instant createdAt) {
        em.createQuery("update TicketModel t set t.createdAt = :createdAt where t.id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", ticket.getId())
                .executeUpdate();
    }

    // ────────────────────────────────────────────────
    // search(): поиск по заголовку и телу
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("search → ищет по заголовку без учёта регистра")
    void search_matchesTitleIgnoringCase() {
        // t1, t4, t5 — совпадение по заголовку; t2 — по телу ("ошибка 402")
        assertThat(run(TicketSpecification.search("ОШИБКА")))
                .containsExactlyInAnyOrder(t1.getId(), t2.getId(), t4.getId(), t5.getId());
    }

    @Test
    @DisplayName("search → ищет по телу заявки")
    void search_matchesBody() {
        assertThat(run(TicketSpecification.search("карта"))).containsExactlyInAnyOrder(t2.getId());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("search → пустой или blank строка → спецификация не создаётся")
    void search_blankOrNull_returnsNull(String text) {
        assertThat(TicketSpecification.search(text)).isNull();
    }

    @Test
    @DisplayName("search → нет совпадений → пустой результат")
    void search_noMatches_returnsEmpty() {
        assertThat(run(TicketSpecification.search("несуществующий текст"))).isEmpty();
    }

    @Test
    @DisplayName("search → совпадение только по телу, но не по заголовку → находит заявку")
    void search_matchesOnlyBodyField() {
        assertThat(run(TicketSpecification.search("статистику"))).containsExactly(t3.getId());
    }

    // ────────────────────────────────────────────────
    // Элементарные фильтры
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("active → отсекает мягко удалённые заявки")
    void active_excludesSoftDeleted() {
        assertThat(run(TicketSpecification.active()))
                .containsExactlyInAnyOrder(t1.getId(), t2.getId(), t3.getId(), t4.getId());
    }

    @Test
    @DisplayName("inPortals → возвращает только заявки указанных порталов")
    void inPortals_restrictsToGivenPortals() {
        assertThat(run(TicketSpecification.inPortals(Set.of(portalA.getId()))))
                .containsExactlyInAnyOrder(t1.getId(), t2.getId(), t3.getId(), t5.getId());
    }

    @Test
    @DisplayName("onlyMy → возвращает только заявки автора")
    void onlyMy_returnsOnlyAuthorTickets() {
        assertThat(run(TicketSpecification.onlyMy(alice.getId())))
                .containsExactlyInAnyOrder(t1.getId(), t3.getId(), t5.getId());
    }

    @Test
    @DisplayName("createdAfter/createdBefore → границы диапазона включительные")
    void createdAfterAndBefore_areInclusive() {
        assertThat(run(TicketSpecification.createdAfter(D1))).contains(t1.getId());
        assertThat(run(TicketSpecification.createdBefore(D1))).contains(t1.getId());
        assertThat(run(TicketSpecification.createdAfter(D1).and(TicketSpecification.createdBefore(D1))))
                .containsExactly(t1.getId());
    }

    @Test
    @DisplayName("withStatus/withPriority → фильтруют по статусу и приоритету")
    void withStatusAndPriority_filterByEnum() {
        assertThat(run(TicketSpecification.withStatus(TicketStatus.CLOSED)))
                .containsExactlyInAnyOrder(t2.getId());
        assertThat(run(TicketSpecification.withPriority(TicketPriority.CRITICAL)))
                .containsExactlyInAnyOrder(t2.getId());
    }

    // ────────────────────────────────────────────────
    // build(): сборка всех фильтров в одну спецификацию
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("build → без фильтров → все активные заявки доступных порталов")
    void build_withoutFilters_returnsAllActiveInPortals() {
        Set<Long> result = run(build(null, null, null, null, null, false, Set.of(portalA.getId())));

        assertThat(result).containsExactlyInAnyOrder(t1.getId(), t2.getId(), t3.getId());
    }

    @Test
    @DisplayName("build → isMyTickets=false → не ограничивает по автору")
    void build_isMyTicketsFalse_doesNotFilterByAuthor() {
        Set<Long> result = run(build(null, null, null, null, null, false, Set.of(portalA.getId())));

        assertThat(result).contains(t2.getId());
    }

    @Test
    @DisplayName("build → isMyTickets=true → только заявки текущего пользователя")
    void build_isMyTicketsTrue_filtersByCurrentUser() {
        Set<Long> result = run(build(null, null, null, null, null, true, Set.of(portalA.getId())));

        assertThat(result).containsExactlyInAnyOrder(t1.getId(), t3.getId());

        Set<Long> bobResult = run(build(null, null, null, null, null, true, Set.of(portalA.getId()), bob.getId()));

        assertThat(bobResult).containsExactly(t2.getId());
    }

    @Test
    @DisplayName("build → статус и приоритет соединяются по И")
    void build_statusAndPriorityAreAnded() {
        // OPEN + CRITICAL одновременно ни у одной заявки нет
        Set<Long> result = run(build(null, null, null, TicketStatus.OPEN, TicketPriority.CRITICAL, false,
                Set.of(portalA.getId())));

        assertThat(result).isEmpty();

        Set<Long> openOnly = run(build(null, null, null, TicketStatus.OPEN, null, false,
                Set.of(portalA.getId())));

        assertThat(openOnly).containsExactly(t1.getId());
    }

    @Test
    @DisplayName("build → диапазон дат отсекает заявки вне периода")
    void build_dateRangeExcludesOutsideTickets() {
        Set<Long> result = run(build(null,
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-12-31T23:59:59Z"),
                null, null, false, Set.of(portalA.getId())));

        assertThat(result).containsExactlyInAnyOrder(t1.getId(), t2.getId());
    }

    @Test
    @DisplayName("build → строка поиска из пробелов не применяется как фильтр")
    void build_blankSearch_isNotApplied() {
        Set<Long> result = run(build("   ", null, null, null, null, false, Set.of(portalA.getId())));

        assertThat(result).containsExactlyInAnyOrder(t1.getId(), t2.getId(), t3.getId());
    }

    @Test
    @DisplayName("build → все фильтры вместе → точное совпадение")
    void build_allFiltersCombined() {
        Set<Long> result = run(build("ошибка",
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-12-31T23:59:59Z"),
                TicketStatus.OPEN, TicketPriority.HIGH, true,
                Set.of(portalA.getId())));

        assertThat(result).containsExactly(t1.getId());
    }

    @Test
    @DisplayName("build → пустой или null набор порталов → IllegalArgumentException (сервис не вызывает build без порталов)")
    void build_withoutPortals_throws() {
        assertThatThrownBy(() -> build(null, null, null, null, null, false, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> build(null, null, null, null, null, false, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ────────────────────────────────────────────────
    // Вспомогательные методы
    // ────────────────────────────────────────────────

    private Specification<TicketModel> build(String search, Instant start, Instant end,
                                             TicketStatus status, TicketPriority priority,
                                             Boolean isMy, Set<Long> portalIds) {
        return build(search, start, end, status, priority, isMy, portalIds, alice.getId());
    }

    private Specification<TicketModel> build(String search, Instant start, Instant end,
                                             TicketStatus status, TicketPriority priority,
                                             Boolean isMy, Set<Long> portalIds, Long userId) {
        return TicketSpecification.build(search, start, end, status, priority, isMy, userId, portalIds);
    }

    private Set<Long> run(Specification<TicketModel> specification) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<TicketModel> query = cb.createQuery(TicketModel.class);
        Root<TicketModel> root = query.from(TicketModel.class);

        Predicate predicate = specification.toPredicate(root, query, cb);
        if (predicate != null) {
            query.where(predicate);
        }

        List<TicketModel> result = em.createQuery(query).getResultList();
        return result.stream().map(TicketModel::getId).collect(Collectors.toSet());
    }

    private UserModel user(String email, String firstName, String lastName) {
        UserModel user = new UserModel();
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPasswordHash("hash");
        em.persist(user);
        return user;
    }

    private PortalModel portal(String name, UserModel owner, boolean isPublic, Set<Long> allowed) {
        PortalModel portal = new PortalModel();
        portal.setName(name);
        portal.setOwner(owner);
        portal.setPublic(isPublic);
        portal.setAllowedUserIds(allowed);
        em.persist(portal);
        return portal;
    }

    private TicketModel ticket(String title, String body, UserModel author, PortalModel portal,
                               TicketStatus status, TicketPriority priority, TicketLiveStatus liveStatus) {
        TicketModel ticket = new TicketModel();
        ticket.setTitle(title);
        ticket.setBody(body);
        ticket.setAuthor(author);
        ticket.setPortal(portal);
        ticket.setTicketStatus(status);
        ticket.setTicketPriority(priority);
        ticket.setTicketLiveStatus(liveStatus);
        em.persist(ticket);
        return ticket;
    }
}
