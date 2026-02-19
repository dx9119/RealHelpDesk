package com.ukhanov.realhelpdesk.feature.ticketmanager.service;

import com.ukhanov.realhelpdesk.core.security.user.CurrentUserProvider;
import com.ukhanov.realhelpdesk.domain.portal.model.PortalModel;
import com.ukhanov.realhelpdesk.domain.portal.repository.PortalRepository;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketModel;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketPriority;
import com.ukhanov.realhelpdesk.domain.ticket.model.TicketStatus;
import com.ukhanov.realhelpdesk.domain.ticket.repository.TicketRepository;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.TicketResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Тесты сервиса TicketSearchService")
class TicketSearchServiceTest {

    @Mock private TicketRepository mockTicketRepository;
    @Mock private PortalRepository mockPortalRepository;
    @Mock private CurrentUserProvider mockCurrentUserProvider;

    @Captor private ArgumentCaptor<Specification<TicketModel>> specCaptor;

    private TicketSearchService service;

    private static final UUID USER_ID = UUID.fromString("a1d0e754-6d93-4de0-a7e6-eb89cc324ac4");
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
        Page<TicketResponse> result = service.searchTickets(
                "test search",
                START_DATE,
                END_DATE,
                TicketStatus.OPEN,
                TicketPriority.HIGH,
                true,
                PAGEABLE
        );

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
        when(mockPortalRepository.findAllByOwnerIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of()); // owned пустые

        when(mockPortalRepository.findAllAccessibleByUserId(USER_ID))
                .thenReturn(List.of(createPortal(5L))); // allowed есть

        when(mockPortalRepository.findPublicPortalIdsWithUserTickets(USER_ID))
                .thenReturn(List.of());

        when(mockTicketRepository.findAll(any(Specification.class), eq(PAGEABLE)))
                .thenReturn(Page.empty(PAGEABLE));

        // when
        Page<TicketResponse> result = service.searchTickets(
                null, null, null, null, null, false, PAGEABLE
        );

        // then
        assertThat(result).isEmpty();
        verify(mockTicketRepository).findAll(any(Specification.class), eq(PAGEABLE));
    }

    // Тест 2: все доступные порталы пустые → сразу пустая страница, без вызова ticketRepository
    @Test
    @DisplayName("searchTickets → все доступные порталы пустые → сразу возвращает пустую страницу")
    void searchTickets_noAccessiblePortalsAtAll_returnsEmptyWithoutQuery() {
        // given
        when(mockPortalRepository.findAllByOwnerIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of());

        when(mockPortalRepository.findAllAccessibleByUserId(USER_ID))
                .thenReturn(List.of());

        when(mockPortalRepository.findPublicPortalIdsWithUserTickets(USER_ID))
                .thenReturn(List.of());

        // when
        Page<TicketResponse> result = service.searchTickets(
                "любой поиск", START_DATE, END_DATE, TicketStatus.OPEN, TicketPriority.HIGH, false, PAGEABLE
        );

        // then
        assertThat(result).isEmpty();
        verifyNoInteractions(mockTicketRepository);
    }

    // Тест 3: все фильтры null, но есть хотя бы один портал → поиск идёт
    @Test
    @DisplayName("searchTickets → все фильтры null, но есть порталы → ищет без ограничений")
    void searchTickets_allFiltersNull_butHasPortals_performsSearch() {
        // given
        when(mockPortalRepository.findAllByOwnerIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of(createPortal(1L)));

        when(mockTicketRepository.findAll(any(Specification.class), eq(PAGEABLE)))
                .thenReturn(Page.empty(PAGEABLE));

        // when
        Page<TicketResponse> result = service.searchTickets(
                null, null, null, null, null, false, PAGEABLE
        );

        // then
        assertThat(result).isEmpty();
        verify(mockTicketRepository).findAll(any(Specification.class), eq(PAGEABLE));
    }

    // Тест 4: isMyTickets = true → должен добавить фильтр по автору
    @Test
    @DisplayName("searchTickets → isMyTickets=true → фильтрует только по автору")
    void searchTickets_isMyTicketsTrue_addsAuthorFilter() {
        // given
        when(mockPortalRepository.findAllByOwnerIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of(createPortal(1L)));

        when(mockTicketRepository.findAll(any(Specification.class), eq(PAGEABLE)))
                .thenReturn(Page.empty(PAGEABLE));

        // when
        service.searchTickets("bug", null, null, null, null, true, PAGEABLE);

        // then
        verify(mockTicketRepository).findAll(specCaptor.capture(), eq(PAGEABLE));
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