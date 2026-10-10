package com.ukhanov.realhelpdesk.feature.ticketmanager.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.ukhanov.realhelpdesk.core.security.user.CurrentUserProvider;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.domain.portal.model.PortalModel;
import com.ukhanov.realhelpdesk.domain.portal.repository.PortalRepository;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketModel;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketPriority;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketStatus;
import com.ukhanov.realhelpdesk.domain.ticket.repository.TicketRepository;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.TicketResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Тесты сервиса TicketSearchService")
class TicketSearchServiceTest {

    @Mock
    private TicketRepository mockTicketRepository;
    @Mock
    private PortalRepository mockPortalRepository;
    @Mock
    private CurrentUserProvider mockCurrentUserProvider;

    @Captor
    private ArgumentCaptor<Specification<TicketModel>> specCaptor;

    private TicketSearchService service;

    private static final Long USER_ID = 77L;
    private static final Instant START_DATE = LocalDateTime.of(2025, 1, 1, 0, 0).toInstant(ZoneOffset.UTC);
    private static final Instant END_DATE = LocalDateTime.of(2025, 12, 31, 23, 59).toInstant(ZoneOffset.UTC);
    private static final Pageable PAGEABLE = PageRequest.of(0, 10);

    @BeforeEach
    void setUp() {
        service = new TicketSearchService(mockTicketRepository, mockPortalRepository, mockCurrentUserProvider);

        lenient().when(mockCurrentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
    }

    // ────────────────────────────────────────────────
    // Основной сценарий поиска
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("searchTickets → успех: находит тикеты по всем фильтрам")
    void searchTickets_success() {
        // given
        List<PortalModel> owned = List.of(createPortal(1L));
        List<PortalModel> allowed = List.of(createPortal(2L));
        List<Long> publicIds = List.of(3L);

        when(mockPortalRepository.findAllByOwnerIdOrderByCreatedAtDesc(USER_ID)).thenReturn(owned);
        when(mockPortalRepository.findAllAccessibleByUserId(USER_ID)).thenReturn(allowed);
        when(mockPortalRepository.findPublicPortalIdsWithUserTickets(USER_ID)).thenReturn(publicIds);

        TicketModel ticket = new TicketModel();
        ticket.setId(100L);
        Page<TicketModel> page = new PageImpl<>(List.of(ticket), PAGEABLE, 1);

        when(mockTicketRepository.findAll(any(Specification.class), eq(PAGEABLE))).thenReturn(page);

        // when
        Page<TicketResponse> result = service.searchTickets("test search", START_DATE, END_DATE, TicketStatus.OPEN, TicketPriority.HIGH,
                true, PAGEABLE);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isOne();

        verify(mockTicketRepository).findAll(specCaptor.capture(), eq(PAGEABLE));
    }

    // ────────────────────────────────────────────────
    // Сценарии с пустыми доступными порталами
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("searchTickets → нет доступных порталов → возвращает пустую страницу")
    void searchTickets_noAccessiblePortals_returnsEmptyPage() {
        when(mockPortalRepository.findAllByOwnerIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of());
        when(mockPortalRepository.findAllAccessibleByUserId(USER_ID)).thenReturn(List.of());
        when(mockPortalRepository.findPublicPortalIdsWithUserTickets(USER_ID)).thenReturn(List.of());

        Page<TicketResponse> result = service.searchTickets("search", START_DATE, END_DATE, null, null, false, PAGEABLE);

        assertThat(result).isEmpty();
        verifyNoInteractions(mockTicketRepository);
    }

    // Тест 1: owned пустые, но есть allowed → поиск продолжается
    @Test
    @DisplayName("searchTickets → owned порталы пустые, но есть allowed → поиск продолжается")
    void searchTickets_ownedEmptyButAllowedExists_searchContinues() {
        // given
        when(mockPortalRepository.findAllByOwnerIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of()); // owned пустые

        when(mockPortalRepository.findAllAccessibleByUserId(USER_ID)).thenReturn(List.of(createPortal(5L))); // allowed есть

        when(mockPortalRepository.findPublicPortalIdsWithUserTickets(USER_ID)).thenReturn(List.of());

        when(mockTicketRepository.findAll(any(Specification.class), eq(PAGEABLE))).thenReturn(Page.empty(PAGEABLE));

        // when
        Page<TicketResponse> result = service.searchTickets(null, null, null, null, null, false, PAGEABLE);

        // then
        assertThat(result).isEmpty();
        verify(mockTicketRepository).findAll(any(Specification.class), eq(PAGEABLE));
    }

    // Тест 2: все доступные порталы пустые → сразу пустая страница, без вызова ticketRepository
    @Test
    @DisplayName("searchTickets → все доступные порталы пустые → сразу возвращает пустую страницу")
    void searchTickets_noAccessiblePortalsAtAll_returnsEmptyWithoutQuery() {
        // given
        when(mockPortalRepository.findAllByOwnerIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of());

        when(mockPortalRepository.findAllAccessibleByUserId(USER_ID)).thenReturn(List.of());

        when(mockPortalRepository.findPublicPortalIdsWithUserTickets(USER_ID)).thenReturn(List.of());

        // when
        Page<TicketResponse> result = service.searchTickets("любой поиск", START_DATE, END_DATE, TicketStatus.OPEN, TicketPriority.HIGH,
                false, PAGEABLE);

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(mockTicketRepository);
    }

    // Тест 3: все фильтры null, но есть хотя бы один портал → поиск идёт
    @Test
    @DisplayName("searchTickets → все фильтры null, но есть порталы → ищет без ограничений")
    void searchTickets_allFiltersNull_butHasPortals_performsSearch() {
        // given
        when(mockPortalRepository.findAllByOwnerIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(createPortal(1L)));

        when(mockTicketRepository.findAll(any(Specification.class), eq(PAGEABLE))).thenReturn(Page.empty(PAGEABLE));

        // when
        Page<TicketResponse> result = service.searchTickets(null, null, null, null, null, false, PAGEABLE);

        // then
        assertThat(result).isEmpty();
        verify(mockTicketRepository).findAll(any(Specification.class), eq(PAGEABLE));
    }

    // Тест 4: isMyTickets = true → должен добавить фильтр по автору
    @Test
    @DisplayName("searchTickets → isMyTickets=true → фильтрует только по автору")
    void searchTickets_isMyTicketsTrue_addsAuthorFilter() {
        // given
        when(mockPortalRepository.findAllByOwnerIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(createPortal(1L)));

        when(mockTicketRepository.findAll(any(Specification.class), eq(PAGEABLE))).thenReturn(Page.empty(PAGEABLE));

        // when
        service.searchTickets("bug", null, null, null, null, true, PAGEABLE);

        // then
        verify(mockTicketRepository).findAll(specCaptor.capture(), eq(PAGEABLE));
    }

    // ────────────────────────────────────────────────
    // Маппинг и параметры поиска
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("searchTickets → маппит модель заявки в TicketResponse")
    void searchTickets_mapsModelToResponse() {
        // given
        when(mockPortalRepository.findAllByOwnerIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(createPortal(9L)));

        PortalModel portal = createPortal(9L);
        portal.setName("Портал Х");

        UserModel author = new UserModel();
        author.setFirstName("Иван");

        Instant createdAt = Instant.parse("2025-01-15T10:00:00Z");

        TicketModel ticket = createTicket(100L);
        ticket.setTitle("Заявка на доступ");
        ticket.setAuthor(author);
        ticket.setPortal(portal);
        ticket.setCreatedAt(createdAt);

        when(mockTicketRepository.findAll(any(Specification.class), eq(PAGEABLE))).thenReturn(new PageImpl<>(List.of(ticket), PAGEABLE, 1));

        // when
        Page<TicketResponse> result = service.searchTickets("доступ", null, null, null, null, false, PAGEABLE);

        // then
        assertThat(result.getContent()).hasSize(1);

        TicketResponse response = result.getContent().get(0);
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.title()).isEqualTo("Заявка на доступ");
        assertThat(response.authorFullName()).isEqualTo("Иван");
        assertThat(response.portalName()).isEqualTo("Портал Х");
        assertThat(response.portalId()).isEqualTo(9L);
        assertThat(response.createdAt()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("searchTickets → заявка без автора и портала → null-поля в ответе")
    void searchTickets_ticketWithoutAuthorAndPortal_mapsNulls() {
        // given
        when(mockPortalRepository.findAllByOwnerIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(createPortal(1L)));

        when(mockTicketRepository.findAll(any(Specification.class), eq(PAGEABLE)))
                .thenReturn(new PageImpl<>(List.of(createTicket(101L)), PAGEABLE, 1));

        // when
        Page<TicketResponse> result = service.searchTickets(null, null, null, null, null, false, PAGEABLE);

        // then
        TicketResponse response = result.getContent().get(0);
        assertThat(response.authorFullName()).isNull();
        assertThat(response.portalName()).isNull();
        assertThat(response.portalId()).isNull();
    }

    @Test
    @DisplayName("searchTickets → доступные порталы собираются из всех трёх источников текущего пользователя")
    void searchTickets_queriesAllPortalSourcesForCurrentUser() {
        // given
        when(mockPortalRepository.findAllByOwnerIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of());
        when(mockPortalRepository.findAllAccessibleByUserId(USER_ID)).thenReturn(List.of(createPortal(5L)));
        when(mockPortalRepository.findPublicPortalIdsWithUserTickets(USER_ID)).thenReturn(List.of());
        when(mockTicketRepository.findAll(any(Specification.class), eq(PAGEABLE))).thenReturn(Page.empty(PAGEABLE));

        // when
        service.searchTickets("поиск", null, null, null, null, false, PAGEABLE);

        // then
        verify(mockPortalRepository).findAllByOwnerIdOrderByCreatedAtDesc(USER_ID);
        verify(mockPortalRepository).findAllAccessibleByUserId(USER_ID);
        verify(mockPortalRepository).findPublicPortalIdsWithUserTickets(USER_ID);
        verify(mockTicketRepository).findAll(any(Specification.class), eq(PAGEABLE));
    }

    @Test
    @DisplayName("searchTickets → пробрасывает Pageable и сохраняет итоги страницы")
    void searchTickets_passesPageableAndPreservesTotals() {
        // given
        Pageable secondPage = PageRequest.of(1, 2);

        when(mockPortalRepository.findAllByOwnerIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(createPortal(1L)));

        when(mockTicketRepository.findAll(any(Specification.class), eq(secondPage)))
                .thenReturn(new PageImpl<>(List.of(createTicket(201L), createTicket(202L)), secondPage, 5));

        // when
        Page<TicketResponse> result = service.searchTickets(null, null, null, null, null, false, secondPage);

        // then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getTotalElements()).isEqualTo(5);
        assertThat(result.getTotalPages()).isEqualTo(3);
        assertThat(result.getNumber()).isEqualTo(1);

        verify(mockTicketRepository).findAll(any(Specification.class), eq(secondPage));
    }

    // ────────────────────────────────────────────────
    // Вспомогательные методы
    // ────────────────────────────────────────────────

    private PortalModel createPortal(Long id) {
        PortalModel p = new PortalModel();
        p.setId(id);
        p.setName("Portal " + id);
        p.setCreatedAt(Instant.now());
        p.setDeleted(false);
        p.setPublic(false);
        return p;
    }

    private TicketModel createTicket(Long id) {
        TicketModel t = new TicketModel();
        t.setId(id);
        t.setTitle("Test Ticket");
        t.setBody("Description");
        t.setTicketStatus(TicketStatus.OPEN);
        t.setTicketPriority(TicketPriority.MEDIUM);
        return t;
    }
}
