package com.ukhanov.realhelpdesk.feature.ticketmanager.service;

import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;
import com.ukhanov.realhelpdesk.core.mail.service.EmailDeliveryService;
import com.ukhanov.realhelpdesk.core.pagination.dto.PageResponse;
import com.ukhanov.realhelpdesk.core.pagination.service.PaginationAdapter;
import com.ukhanov.realhelpdesk.core.security.accesscontrol.AccessValidationService;
import com.ukhanov.realhelpdesk.core.security.user.CurrentUserProvider;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.domain.portal.model.PortalModel;
import com.ukhanov.realhelpdesk.domain.portal.service.PortalDomainService;
import com.ukhanov.realhelpdesk.domain.ticket.model.*;
import com.ukhanov.realhelpdesk.domain.ticket.repository.TicketRepository;
import com.ukhanov.realhelpdesk.domain.ticket.service.TicketDomainService;
import com.ukhanov.realhelpdesk.feature.portalmanager.exception.PortalException;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.CreateTicketRequest;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.CreateTicketResponse;
import com.ukhanov.realhelpdesk.feature.ticketmanager.dto.TicketResponseOld;
import com.ukhanov.realhelpdesk.feature.ticketmanager.exception.TicketException;
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

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Тесты сервиса TicketManageService")
class TicketManageServiceTest {

    @Mock private TicketDomainService mockTicketDomainService;
    @Mock private CurrentUserProvider mockCurrentUserProvider;
    @Mock private PortalDomainService mockPortalDomainService;
    @Mock private PaginationAdapter mockPaginationAdapter;
    @Mock private EmailDeliveryService mockEmailDeliveryService;
    @Mock private AccessValidationService mockAccessValidationService;
    @Mock private TicketRepository mockTicketRepository;

    @Captor private ArgumentCaptor<TicketModel> ticketCaptor;

    private TicketManageService service;

    private static final Long TICKET_ID = 42L;
    private static final Long PORTAL_ID = 100L;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final Pageable DEFAULT_PAGEABLE = PageRequest.of(0, 10);

    @BeforeEach
    void setUp() {
        service = new TicketManageService(
                mockTicketDomainService,
                mockCurrentUserProvider,
                mockPortalDomainService,
                mockPaginationAdapter,
                mockEmailDeliveryService,
                mockAccessValidationService,
                mockTicketRepository
        );

        UserModel currentUser = new UserModel();
        currentUser.setId(USER_ID);
        currentUser.setEmail("test@user.com");
        lenient().when(mockCurrentUserProvider.getCurrentUserModel()).thenReturn(currentUser);
    }

    // ────────────────────────────────────────────────
    // getTicketById
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("getTicketById → успех")
    void getTicketById_success() throws TicketException {
        TicketModel ticket = defaultTicket(TICKET_ID);
        when(mockTicketDomainService.findTicketById(TICKET_ID)).thenReturn(ticket);

        TicketResponseOld response = service.getTicketById(TICKET_ID);

        assertThat(response).isNotNull();
        verify(mockTicketDomainService).findTicketById(TICKET_ID);
    }

    @Test
    @DisplayName("getTicketById → null id → NPE")
    void getTicketById_nullId_throwsNPE() {
        assertThatThrownBy(() -> service.getTicketById(null))
                .isInstanceOf(NullPointerException.class);
    }

    // ────────────────────────────────────────────────
    // createTicket
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("createTicket → успех")
    void createTicket_success() throws Exception {
        CreateTicketRequest request = new CreateTicketRequest();
        request.setTitle("New Issue");

        PortalModel portal = defaultPortal(PORTAL_ID);
        when(mockPortalDomainService.getPortalById(PORTAL_ID)).thenReturn(portal);

        TicketModel saved = defaultTicket(TICKET_ID);
        when(mockTicketDomainService.saveTicket(any())).thenReturn(saved);

        CreateTicketResponse response = service.createTicket(request, PORTAL_ID);

        assertThat(response.getMessage()).contains(String.valueOf(TICKET_ID));

        verify(mockTicketDomainService).saveTicket(ticketCaptor.capture());
        assertThat(ticketCaptor.getValue().getTitle()).isEqualTo("New Issue");

        verify(mockEmailDeliveryService).initNotifyPortalUsers(
                eq(portal),
                anyString(),
                anyString(),
                eq(NotificationEvent.NEW_TICKET)
        );
    }

    // ────────────────────────────────────────────────
    // getPageTickets
    // ────────────────────────────────────────────────

    @Test
    @DisplayName("getPageTickets → успех")
    void getPageTickets_success() throws TicketException, PortalException {
        PageRequest pageRequest = PageRequest.of(0, 10);
        when(mockPaginationAdapter.buildPageRequest(0, 10, "title", "asc"))
                .thenReturn(pageRequest);

        TicketModel ticketModel = defaultTicket(1L);
        Page<TicketModel> ticketPage = new PageImpl<>(List.of(ticketModel), pageRequest, 1);
        when(mockTicketDomainService.getTicketsPageByPortalId(PORTAL_ID, pageRequest))
                .thenReturn(ticketPage);

        // Создаём маппинг вручную
        TicketResponseOld dto = new TicketResponseOld.Builder()
                .id(1L)
                .title("Test Ticket")
                .build();

        Page<TicketResponseOld> mappedPage = ticketPage.map(t -> dto); // имитируем map

        PageResponse<TicketResponseOld> expected = new PageResponse<>();
        expected.setPage(0);
        expected.setSize(10);
        expected.setTotalElements(1L);
        expected.setTotalPages(1);
        expected.setContent(List.of(dto));

        // Стабим mapToResponse с mappedPage (имитируем, что map уже прошёл)
        when(mockPaginationAdapter.<TicketResponseOld>mapToResponse(
                any(Page.class), eq("title"), eq("asc")
        )).thenReturn(expected);

        PageResponse<TicketResponseOld> result = service.getPageTickets(
                PORTAL_ID, 0, 10, "title", "asc"
        );

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1L);
        assertThat(result.getContent()).hasSize(1);
    }


    // ────────────────────────────────────────────────
    // Фабрики
    // ────────────────────────────────────────────────

    private TicketModel defaultTicket(Long id) {
        TicketModel t = new TicketModel();
        t.setId(id);
        t.setTitle("Test Ticket");
        t.setBody("Description");
        t.setTicketStatus(TicketStatus.OPEN);
        t.setTicketPriority(TicketPriority.MEDIUM);
        t.setTicketLiveStatus(TicketLiveStatus.ACTIVE);
        t.setCreatedAt(Instant.now());

        UserModel author = new UserModel();
        author.setId(USER_ID);
        t.setAuthor(author);

        t.setPortal(defaultPortal(PORTAL_ID));

        return t;
    }

    private PortalModel defaultPortal(Long id) {
        PortalModel p = new PortalModel();
        p.setId(id);
        p.setName("Test Portal");
        return p;
    }
}