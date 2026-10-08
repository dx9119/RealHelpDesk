package com.ukhanov.realhelpdesk.feature.portalmanager.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import jakarta.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.ukhanov.realhelpdesk.core.mail.model.EmailTemplates;
import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;
import com.ukhanov.realhelpdesk.core.mail.service.EmailDeliveryService;
import com.ukhanov.realhelpdesk.core.security.auth.login.service.LoginService;
import com.ukhanov.realhelpdesk.core.security.limiter.exception.LimitException;
import com.ukhanov.realhelpdesk.core.security.limiter.service.LimitService;
import com.ukhanov.realhelpdesk.core.security.user.CurrentUserProvider;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.core.security.user.model.UserStatus;
import com.ukhanov.realhelpdesk.core.security.user.service.UserDomainService;
import com.ukhanov.realhelpdesk.domain.portal.model.PortalHistoryEvent;
import com.ukhanov.realhelpdesk.domain.portal.model.PortalModel;
import com.ukhanov.realhelpdesk.domain.portal.model.PortalTransferRequestModel;
import com.ukhanov.realhelpdesk.domain.portal.model.PortalTransferStatus;
import com.ukhanov.realhelpdesk.domain.portal.repository.PortalTransferRequestRepository;
import com.ukhanov.realhelpdesk.domain.portal.service.PortalDomainService;
import com.ukhanov.realhelpdesk.domain.portal.service.PortalHistoryService;
import com.ukhanov.realhelpdesk.feature.notificationmanager.service.NotificationPublisher;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.PortalTransferConfirmRequest;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.PortalTransferRejectRequest;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.PortalTransferRequest;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.PortalTransferResponse;
import com.ukhanov.realhelpdesk.feature.portalmanager.exception.PortalException;

/**
 * Передача владения порталом: владелец инициирует запрос (своим паролем и причиной), предлагаемый владелец подтверждает или отклоняет
 * запрос своим паролем, владелец может отозвать запрос до решения. Запрос действует ограниченный срок и закрывается лениво как EXPIRED при
 * следующем обращении.
 *
 * <p>
 * Каждое решение пишется в историю портала одной транзакцией с изменением запроса; оповещения (in-app и email) критичной операцией не
 * считаются — их сбой логируется и не откатывает передачу.
 * </p>
 */
@Service
public class PortalOwnerTransferService {

    /** Срок действия запроса на передачу: после него запрос лениво закрывается как EXPIRED. */
    private static final Duration TRANSFER_REQUEST_TTL = Duration.ofHours(72);

    private static final Logger logger = LoggerFactory.getLogger(PortalOwnerTransferService.class);

    private final CurrentUserProvider currentUserProvider;
    private final PortalDomainService portalDomainService;
    private final PortalTransferRequestRepository transferRequestRepository;
    private final PortalHistoryService portalHistoryService;
    private final UserDomainService userDomainService;
    private final LoginService loginService;
    private final LimitService limitService;
    private final EmailDeliveryService emailDeliveryService;
    private final EmailTemplates emailTemplates;
    private final NotificationPublisher notificationPublisher;

    public PortalOwnerTransferService(CurrentUserProvider currentUserProvider, PortalDomainService portalDomainService,
            PortalTransferRequestRepository transferRequestRepository, PortalHistoryService portalHistoryService,
            UserDomainService userDomainService, LoginService loginService, LimitService limitService,
            EmailDeliveryService emailDeliveryService, EmailTemplates emailTemplates, NotificationPublisher notificationPublisher) {
        this.currentUserProvider = currentUserProvider;
        this.portalDomainService = portalDomainService;
        this.transferRequestRepository = transferRequestRepository;
        this.portalHistoryService = portalHistoryService;
        this.userDomainService = userDomainService;
        this.loginService = loginService;
        this.limitService = limitService;
        this.emailDeliveryService = emailDeliveryService;
        this.emailTemplates = emailTemplates;
        this.notificationPublisher = notificationPublisher;
    }

    /** Инициирование передачи владельцем: пароль владельца подтверждает личность, активный запрос может быть только один. */
    @Transactional
    public PortalTransferResponse initiate(Long portalId, PortalTransferRequest request) throws PortalException {
        Objects.requireNonNull(request, "Запрос на передачу не должен быть null");

        PortalModel portal = portalDomainService.getPortalById(portalId);
        UserModel initiator = currentUserProvider.getCurrentUserModel();
        requireOwner(portal, initiator);

        if (!loginService.isPasswordValid(request.getPassword(), initiator.getPasswordHash())) {
            throw new PortalException("Неверный пароль", HttpStatus.UNAUTHORIZED);
        }

        UserModel proposed = findActiveUserByEmail(request.getEmail());

        if (proposed.getId().equals(initiator.getId())) {
            throw new PortalException("Нельзя передать портал самому себе");
        }

        Optional<PortalTransferRequestModel> existing = findPending(portalId);
        if (existing.isPresent()) {
            if (!expireIfStale(existing.get())) {
                throw new PortalException("Активный запрос на передачу портала уже существует");
            }
        }

        PortalTransferRequestModel transfer = new PortalTransferRequestModel();
        transfer.setPortalId(portalId);
        transfer.setInitiatorId(initiator.getId());
        transfer.setProposedOwnerId(proposed.getId());
        transfer.setReasonInitiator(request.getReason());
        transfer.setKeepOldOwner(Boolean.TRUE.equals(request.getKeepOldOwnerAsMember()));
        transfer.setStatus(PortalTransferStatus.PENDING);
        transfer.setExpiresAt(Instant.now().plus(TRANSFER_REQUEST_TTL));
        transferRequestRepository.save(transfer);

        portalHistoryService.record(portalId, PortalHistoryEvent.TRANSFER_REQUESTED, initiator.getId(), proposed.getId(),
                request.getReason(), null, null, null);

        logger.info("Инициирована передача портала {} с пользователя {} на пользователя {}", portalId, initiator.getId(), proposed.getId());
        notifyTransferRequested(portal, transfer, initiator, proposed);

        return toResponse(transfer, portal, initiator, proposed);
    }

    /** Подтверждение передачи предлагаемым владельцем: его пароль и причина; портал переходит новому владельцу. */
    @Transactional
    public PortalTransferResponse confirm(Long portalId, PortalTransferConfirmRequest request) throws PortalException, LimitException {
        Objects.requireNonNull(request, "Запрос на подтверждение не должен быть null");

        PortalModel portal = portalDomainService.getPortalById(portalId);
        PortalTransferRequestModel transfer = requirePendingForCurrentUser(portalId);

        if (!loginService.isPasswordValid(request.getPassword(), currentUserProvider.getCurrentUserModel().getPasswordHash())) {
            throw new PortalException("Неверный пароль", HttpStatus.UNAUTHORIZED);
        }

        UserModel proposed = currentUserProvider.getCurrentUserModel();
        if (proposed.getUserStatus() != UserStatus.ACTIVE) {
            throw new PortalException("Учётная запись нового владельца не активна — передача невозможна");
        }

        UserModel oldOwner = portal.getOwner();

        Set<Long> allowed = portal.getAllowedUserIds() == null ? new HashSet<>() : new HashSet<>(portal.getAllowedUserIds());
        allowed.remove(proposed.getId());
        boolean keepOldOwner = transfer.isKeepOldOwner();
        if (keepOldOwner) {
            allowed.add(oldOwner.getId());
        }
        if (limitService.violatesSharedUserPortalLimit(proposed, portal, allowed.size())) {
            throw new LimitException("Достигнут лимит на количество пользователей с доступом к порталу");
        }

        portal.setOwner(proposed);
        portal.setAllowedUserIds(allowed);
        portalDomainService.savePortal(portal);

        transfer.setStatus(PortalTransferStatus.ACCEPTED);
        transfer.setReasonProposed(request.getReason());
        transfer.setDecidedAt(Instant.now());
        transferRequestRepository.save(transfer);

        portalHistoryService.record(portalId, PortalHistoryEvent.TRANSFER_ACCEPTED, proposed.getId(), oldOwner.getId(), request.getReason(),
                null, null, null);

        logger.info("Портал {} передан: владелец {} → {}", portalId, oldOwner.getId(), proposed.getId());
        notifyTransferAccepted(portal, transfer, oldOwner, proposed, allowed);

        return toResponse(transfer, portal, oldOwner, proposed);
    }

    /** Отклонение передачи предлагаемым владельцем: причина уходит инициатору и в историю. */
    @Transactional
    public PortalTransferResponse reject(Long portalId, PortalTransferRejectRequest request) throws PortalException {
        Objects.requireNonNull(request, "Запрос на отклонение не должен быть null");

        PortalModel portal = portalDomainService.getPortalById(portalId);
        PortalTransferRequestModel transfer = requirePendingForCurrentUser(portalId);
        UserModel proposed = currentUserProvider.getCurrentUserModel();
        UserModel initiator = safeUser(transfer.getInitiatorId());

        transfer.setStatus(PortalTransferStatus.REJECTED);
        transfer.setReasonProposed(request.getReason());
        transfer.setDecidedAt(Instant.now());
        transferRequestRepository.save(transfer);

        portalHistoryService.record(portalId, PortalHistoryEvent.TRANSFER_REJECTED, proposed.getId(), initiator.getId(),
                request.getReason(), null, null, null);

        logger.info("Передача портала {} отклонена пользователем {}", portalId, proposed.getId());
        notifyTransferRejected(portal, transfer, initiator, proposed);

        return toResponse(transfer, portal, initiator, proposed);
    }

    /** Отзыв запроса владельцем до решения предлагаемого владельца. */
    @Transactional
    public PortalTransferResponse cancel(Long portalId) throws PortalException {
        PortalModel portal = portalDomainService.getPortalById(portalId);
        UserModel initiator = currentUserProvider.getCurrentUserModel();
        requireOwner(portal, initiator);

        PortalTransferRequestModel transfer = findPending(portalId).orElseThrow(() -> notFoundRequest());

        transfer.setStatus(PortalTransferStatus.CANCELLED);
        transfer.setDecidedAt(Instant.now());
        transferRequestRepository.save(transfer);

        portalHistoryService.record(portalId, PortalHistoryEvent.TRANSFER_CANCELLED, initiator.getId(), transfer.getProposedOwnerId(), null,
                null, null, null);

        logger.info("Передача портала {} отозвана владельцем {}", portalId, initiator.getId());
        notifyTransferCancelled(portal, transfer, initiator);

        return toResponse(transfer, portal, initiator, null);
    }

    /**
     * Активный запрос: виден инициатору (владельцу) и предлагаемому владельцу — у последнего ещё нет доступа к порталу, поэтому авторизация
     * проверяется здесь, а не через @PreAuthorize. Истёкший запрос закрывается лениво и дальше не отдаётся.
     */
    public PortalTransferResponse getPending(Long portalId) throws PortalException {
        PortalModel portal = portalDomainService.getPortalById(portalId);
        PortalTransferRequestModel transfer = findPending(portalId).orElseThrow(PortalOwnerTransferService::notFoundRequest);

        Long currentUserId = currentUserProvider.getCurrentUserId();
        boolean visible = currentUserId.equals(transfer.getInitiatorId()) || currentUserId.equals(transfer.getProposedOwnerId());
        if (!visible) {
            // чужим пользователям не раскрываем существование запроса
            throw notFoundRequest();
        }

        if (expireIfStale(transfer)) {
            throw notFoundRequest();
        }

        return toResponse(transfer, portal, safeUser(transfer.getInitiatorId()), safeUser(transfer.getProposedOwnerId()));
    }

    /** Истёкший активный запрос закрывается как EXPIRED с историей и оповещением инициатора; возвращает true, если закрыли. */
    private boolean expireIfStale(PortalTransferRequestModel transfer) {
        if (transfer.getExpiresAt().isAfter(Instant.now())) {
            return false;
        }

        transfer.setStatus(PortalTransferStatus.EXPIRED);
        transfer.setDecidedAt(Instant.now());
        transferRequestRepository.save(transfer);

        portalHistoryService.record(transfer.getPortalId(), PortalHistoryEvent.TRANSFER_EXPIRED, null, transfer.getProposedOwnerId(), null,
                null, null, null);

        logger.info("Истёк срок передачи портала {}, запрос {} закрыт", transfer.getPortalId(), transfer.getId());

        PortalModel portal = safePortal(transfer.getPortalId());
        UserModel initiator = safeUser(transfer.getInitiatorId());
        if (portal != null && initiator != null) {
            notifyTransferExpired(portal, transfer, initiator);
        }
        return true;
    }

    private PortalTransferRequestModel requirePendingForCurrentUser(Long portalId) throws PortalException {
        PortalTransferRequestModel transfer = findPending(portalId).orElseThrow(PortalOwnerTransferService::notFoundRequest);

        Long currentUserId = currentUserProvider.getCurrentUserId();
        if (!currentUserId.equals(transfer.getProposedOwnerId())) {
            // только предлагаемый владелец видит и решает запрос; остальным — как будто запроса нет
            throw notFoundRequest();
        }

        if (expireIfStale(transfer)) {
            throw new PortalException("Срок действия запроса на передачу портала истёк");
        }
        return transfer;
    }

    private Optional<PortalTransferRequestModel> findPending(Long portalId) {
        return transferRequestRepository.findByPortalIdAndStatus(portalId, PortalTransferStatus.PENDING);
    }

    private void requireOwner(PortalModel portal, UserModel user) throws PortalException {
        if (user == null || portal.getOwner() == null || !user.getId().equals(portal.getOwner().getId())) {
            throw new PortalException("Передачу портала может выполнять только владелец", HttpStatus.FORBIDDEN);
        }
    }

    private UserModel findActiveUserByEmail(String email) throws PortalException {
        UserModel user;
        try {
            user = userDomainService.getUserByEmail(email);
        } catch (UsernameNotFoundException e) {
            throw new PortalException("Пользователь с таким email не найден", HttpStatus.NOT_FOUND, e);
        }
        if (user.getUserStatus() != UserStatus.ACTIVE) {
            throw new PortalException("Учётная запись нового владельца не активна — передача невозможна");
        }
        return user;
    }

    private static PortalException notFoundRequest() {
        return new PortalException("Активный запрос на передачу портала не найден", HttpStatus.NOT_FOUND);
    }

    // --- Оповещения: не критичны для операции, любой сбой только логируется ---

    private void notifyTransferRequested(PortalModel portal, PortalTransferRequestModel transfer, UserModel initiator, UserModel proposed) {
        String initiatorName = displayName(initiator);
        String participantsTitle = "Владелец портала «" + portal.getName() + "» (" + initiatorName + ") начал передачу портала";
        String proposedTitle = "Вам передают портал «" + portal.getName() + "» (портал #" + portal.getId() + ")";

        Set<Long> participants = portalUserIds(portal);
        participants.remove(proposed.getId());

        notificationPublisher.publishToUsers(participants, NotificationEvent.PORTAL_TRANSFER_REQUESTED, initiator.getId(), null,
                portal.getId(), participantsTitle);
        notificationPublisher.publishToUsers(Set.of(proposed.getId()), NotificationEvent.PORTAL_TRANSFER_REQUESTED, initiator.getId(), null,
                portal.getId(), proposedTitle);

        quietly("инициирование передачи портала " + portal.getId(), () -> {
            if (!participants.isEmpty()) {
                emailDeliveryService.sendPortalUsersNotification(participants,
                        emailTemplates.portalTransferRequestedSubject(portal.getId()),
                        emailTemplates.portalTransferRequestedBody(portal.getId(), initiatorName, transfer.getReasonInitiator()),
                        NotificationEvent.PORTAL_TRANSFER_REQUESTED);
            }
            emailDeliveryService.sendUserNotification(proposed.getEmail(), emailTemplates.portalTransferProposedSubject(portal.getId()),
                    emailTemplates.portalTransferProposedBody(portal.getId(), initiatorName, transfer.getReasonInitiator()),
                    NotificationEvent.PORTAL_TRANSFER_REQUESTED);
        });
    }

    private void notifyTransferAccepted(PortalModel portal, PortalTransferRequestModel transfer, UserModel oldOwner, UserModel newOwner,
            Set<Long> newAllowedUsers) {
        String newOwnerName = displayName(newOwner);
        String title = "Портал «" + portal.getName() + "» теперь принадлежит " + newOwnerName;

        Set<Long> recipients = new HashSet<>(newAllowedUsers);
        recipients.add(oldOwner.getId());

        notificationPublisher.publishToUsers(recipients, NotificationEvent.PORTAL_TRANSFER_ACCEPTED, newOwner.getId(), null, portal.getId(),
                title);
        recipients.remove(newOwner.getId());

        quietly("подтверждение передачи портала " + portal.getId(),
                () -> emailDeliveryService.sendPortalUsersNotification(recipients,
                        emailTemplates.portalTransferAcceptedSubject(portal.getId()),
                        emailTemplates.portalTransferAcceptedBody(portal.getId(), newOwnerName, transfer.getReasonInitiator()),
                        NotificationEvent.PORTAL_TRANSFER_ACCEPTED));
    }

    private void notifyTransferRejected(PortalModel portal, PortalTransferRequestModel transfer, UserModel initiator, UserModel proposed) {
        String proposedName = displayName(proposed);
        String title = "Запрос на передачу портала «" + portal.getName() + "» отклонён";

        notificationPublisher.publishToUsers(Set.of(initiator != null ? initiator.getId() : transfer.getInitiatorId()),
                NotificationEvent.PORTAL_TRANSFER_REJECTED, proposed.getId(), null, portal.getId(), title);

        if (initiator != null) {
            quietly("отклонение передачи портала " + portal.getId(),
                    () -> emailDeliveryService.sendUserNotification(initiator.getEmail(),
                            emailTemplates.portalTransferRejectedSubject(portal.getId()),
                            emailTemplates.portalTransferRejectedBody(portal.getId(), proposedName, transfer.getReasonProposed()),
                            NotificationEvent.PORTAL_TRANSFER_REJECTED));
        }
    }

    private void notifyTransferCancelled(PortalModel portal, PortalTransferRequestModel transfer, UserModel initiator) {
        String initiatorName = displayName(initiator);
        String title = "Передача портала «" + portal.getName() + "» отозвана владельцем";
        UserModel proposed = safeUser(transfer.getProposedOwnerId());

        notificationPublisher.publishToUsers(Set.of(transfer.getProposedOwnerId()), NotificationEvent.PORTAL_TRANSFER_CANCELLED,
                initiator.getId(), null, portal.getId(), title);

        if (proposed != null) {
            quietly("отзыв передачи портала " + portal.getId(),
                    () -> emailDeliveryService.sendUserNotification(proposed.getEmail(),
                            emailTemplates.portalTransferCancelledSubject(portal.getId()),
                            emailTemplates.portalTransferCancelledBody(portal.getId(), initiatorName),
                            NotificationEvent.PORTAL_TRANSFER_CANCELLED));
        }
    }

    private void notifyTransferExpired(PortalModel portal, PortalTransferRequestModel transfer, UserModel initiator) {
        String title = "Истёк срок передачи портала «" + portal.getName() + "»";

        notificationPublisher.publishToUsers(Set.of(initiator.getId()), NotificationEvent.PORTAL_TRANSFER_EXPIRED, null, null,
                portal.getId(), title);

        quietly("истечение срока передачи портала " + portal.getId(),
                () -> emailDeliveryService.sendUserNotification(initiator.getEmail(),
                        emailTemplates.portalTransferExpiredSubject(portal.getId()),
                        emailTemplates.portalTransferExpiredBody(portal.getId()), NotificationEvent.PORTAL_TRANSFER_EXPIRED));
    }

    private Set<Long> portalUserIds(PortalModel portal) {
        Set<Long> users = new HashSet<>();
        if (portal.getOwner() != null) {
            users.add(portal.getOwner().getId());
        }
        if (portal.getAllowedUserIds() != null) {
            users.addAll(portal.getAllowedUserIds());
        }
        return users;
    }

    private PortalTransferResponse toResponse(PortalTransferRequestModel transfer, PortalModel portal, UserModel initiator,
            UserModel proposed) {
        UserModel proposedUser = proposed != null ? proposed : safeUser(transfer.getProposedOwnerId());
        PortalTransferResponse response = new PortalTransferResponse();
        response.setId(transfer.getId());
        response.setPortalId(portal.getId());
        response.setPortalName(portal.getName());
        response.setInitiatorName(initiator != null ? displayName(initiator) : null);
        response.setProposedOwnerEmail(proposedUser != null ? proposedUser.getEmail() : null);
        response.setReasonInitiator(transfer.getReasonInitiator());
        response.setReasonProposed(transfer.getReasonProposed());
        response.setKeepOldOwnerAsMember(transfer.isKeepOldOwner());
        response.setStatus(transfer.getStatus().name());
        response.setCreatedAt(transfer.getCreatedAt());
        response.setExpiresAt(transfer.getExpiresAt());
        response.setDecidedAt(transfer.getDecidedAt());
        return response;
    }

    private UserModel safeUser(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            return userDomainService.getUserById(userId);
        } catch (UsernameNotFoundException e) {
            return null;
        }
    }

    private PortalModel safePortal(Long portalId) {
        try {
            return portalDomainService.getPortalById(portalId);
        } catch (Exception e) {
            return null;
        }
    }

    private String displayName(UserModel user) {
        if (user == null) {
            return "Пользователь не существует";
        }
        String name = (user.getLastName() + " " + user.getFirstName()).trim();
        return name.isBlank() ? user.getEmail() : name;
    }

    private void quietly(String what, EmailAction action) {
        try {
            action.run();
        } catch (Exception e) {
            logger.warn("Не удалось отправить email ({}) : {}", what, e.getMessage());
        }
    }

    @FunctionalInterface
    private interface EmailAction {
        void run() throws Exception;
    }
}
