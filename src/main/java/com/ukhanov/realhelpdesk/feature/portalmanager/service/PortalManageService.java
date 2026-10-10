package com.ukhanov.realhelpdesk.feature.portalmanager.service;

import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.mail.MessagingException;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.ukhanov.realhelpdesk.core.mail.model.EmailTemplates;
import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;
import com.ukhanov.realhelpdesk.core.mail.service.EmailDeliveryService;
import com.ukhanov.realhelpdesk.core.pagination.dto.PageResponse;
import com.ukhanov.realhelpdesk.core.pagination.service.PaginationAdapter;
import com.ukhanov.realhelpdesk.core.security.accesscontrol.AccessValidationService;
import com.ukhanov.realhelpdesk.core.security.limiter.exception.LimitException;
import com.ukhanov.realhelpdesk.core.security.limiter.service.LimitService;
import com.ukhanov.realhelpdesk.core.security.user.CurrentUserProvider;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.core.security.user.repository.UserDetailsProjection;
import com.ukhanov.realhelpdesk.core.security.user.service.UserDomainService;
import com.ukhanov.realhelpdesk.domain.portal.model.PortalHistoryEvent;
import com.ukhanov.realhelpdesk.domain.portal.model.PortalHistoryModel;
import com.ukhanov.realhelpdesk.domain.portal.model.PortalModel;
import com.ukhanov.realhelpdesk.domain.portal.service.PortalDomainService;
import com.ukhanov.realhelpdesk.domain.portal.service.PortalHistoryService;
import com.ukhanov.realhelpdesk.feature.notificationmanager.service.NotificationPublisher;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.CreatePortalRequest;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.CreatePortalResponse;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.DeleteResult;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.PortalHistoryResponse;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.PortalInfoResponse;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.PortalResponse;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.PortalSettingsResponse;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.UpdatePortalInfoRequest;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.UserInfo;
import com.ukhanov.realhelpdesk.feature.portalmanager.exception.PortalException;
import com.ukhanov.realhelpdesk.feature.portalmanager.mapper.PortalMapper;
import com.ukhanov.realhelpdesk.feature.usermanager.exception.UserManageException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Service
public class PortalManageService {

    private static final Set<String> SORTABLE_PORTAL_FIELDS = Set.of("createdAt", "name");

    private final CurrentUserProvider currentUserProvider;
    private final PortalDomainService portalDomainService;
    private final PaginationAdapter paginationAdapter;
    private final PortalUtilsService portalUtilsService;
    private final AccessValidationService accessValidationService;
    private final LimitService limitService;
    private final UserDomainService userDomainService;
    private final EmailDeliveryService emailDeliveryService;
    private final EmailTemplates emailTemplates;
    private final NotificationPublisher notificationPublisher;
    private final PortalHistoryService portalHistoryService;
    private final PortalMapper portalMapper;

    public CreatePortalResponse createPortal(CreatePortalRequest request)
            throws PortalException, LimitException, UserManageException, MessagingException, UnsupportedEncodingException {
        Objects.requireNonNull(request, "Запрос на создание портала не должен быть null");

        UserModel userModel = currentUserProvider.getCurrentUserModel();
        PortalModel portal = portalMapper.toEntity(request, userModel);

        if (!userModel.isEmailVerified()) {
            throw new UserManageException("Подтвердите ваш адрес электронной почты, далее вы сможете создать портал");
        }

        if (limitService.hasUserReachedPortalLimit(userModel)) {
            throw new LimitException("Пользователь достиг лимита на количество порталов");
        }

        if (portalDomainService.isPortalExistByName(portal.getOwner().getId(), portal.getName())) {
            throw new PortalException("Портал с именем '" + portal.getName() + "' уже существует");
        }

        // Тут портал получил ID
        PortalModel savePortal = portalDomainService.savePortal(portal);

        logger.info("Портал создан для пользователя {} с именем '{}'", userModel.getId(), portal.getName());

        // In-app оповещение о новом портале: на момент создания участников ещё нет, получателем остаётся только создатель —
        // publisher исключает автора, поэтому строк не появится (точка входа нужна для паритета с email)
        notificationPublisher.publishToPortalUsers(portal, NotificationEvent.NEW_PORTAL, userModel.getId(), null, portal.getName());

        // Отправляем письмо
        emailDeliveryService.initNotifyPortalUsers(portal, emailTemplates.portalCreatedSubject(savePortal.getId()),
                emailTemplates.portalCreatedBody(savePortal.getId()), NotificationEvent.NEW_PORTAL);

        return new CreatePortalResponse(savePortal.getId());
    }

    public List<PortalResponse> getAllPortals() {

        UserModel userModel = currentUserProvider.getCurrentUserModel();
        return portalDomainService.getPortalsByOwnerId(userModel.getId()).stream().map(portalMapper::toResponse).toList();
    }

    public List<Long> getAllPortalIds() {

        UserModel userModel = currentUserProvider.getCurrentUserModel();
        return portalDomainService.getPortalsByOwnerId(userModel.getId()).stream().map(PortalModel::getId).toList();
    }

    public PageResponse<PortalResponse> getPagePortalsByOwner(int page, int size, String sortBy, String order) {
        UserModel userModel = currentUserProvider.getCurrentUserModel();
        PageRequest pageRequest = paginationAdapter.buildPageRequest(page, size, sortBy, order, SORTABLE_PORTAL_FIELDS);

        Page<PortalModel> portalPage = portalDomainService.getPortalsPageByOwnerId(userModel.getId(), pageRequest);
        Page<PortalResponse> mappedPage = portalPage.map(portalMapper::toResponse);

        return paginationAdapter.mapToResponse(mappedPage, sortBy, order);
    }

    public PageResponse<PortalResponse> getPagePortalsByAccess(int page, int size, String sortBy, String order) {
        UserModel userModel = currentUserProvider.getCurrentUserModel();
        PageRequest pageRequest = paginationAdapter.buildPageRequest(page, size, sortBy, order, SORTABLE_PORTAL_FIELDS);

        Page<PortalModel> portalPage = portalDomainService.getPortalPageAccessByUser(userModel.getId(), pageRequest);
        Page<PortalResponse> mappedPage = portalPage.map(portalMapper::toResponse);

        return paginationAdapter.mapToResponse(mappedPage, sortBy, order);
    }

    @Transactional
    public void setPortalStatus(Long portalId, boolean isPublic) throws PortalException {
        Objects.requireNonNull(portalId, "portalId не должен быть null");
        logger.info("Публичность портала {} изменена на {}", portalId, isPublic);

        PortalModel portal;
        try {
            portal = portalDomainService.getPortalById(portalId);
        } catch (IllegalArgumentException e) {
            throw new PortalException("Портал с ID " + portalId + " не найден", HttpStatus.NOT_FOUND, e);
        }
        boolean oldPublic = portal.isPublic();
        portal.setPublic(isPublic);
        portalDomainService.savePortal(portal);

        if (oldPublic != isPublic) {
            portalHistoryService.record(portalId, PortalHistoryEvent.VISIBILITY_CHANGED, currentUserProvider.getCurrentUserId(), null, null,
                    "visibility", String.valueOf(oldPublic), String.valueOf(isPublic));
        }
    }

    public Boolean getStatusPortal(Long portalId) throws PortalException {
        PortalModel portalModel = portalDomainService.getPortalById(portalId);
        return portalModel.isPublic();
    }

    // todo добавить валидацию id
    @Transactional
    public void addUserForPortal(Long portalId, Set<Long> newAccessUserId) throws PortalException, LimitException {
        Objects.requireNonNull(portalId, "portalId не должен быть null");
        Objects.requireNonNull(newAccessUserId, "newAccessUserId не должен быть null");

        portalUtilsService.validateIdList(newAccessUserId);

        PortalModel portal = portalDomainService.getPortalById(portalId);
        UserModel user = currentUserProvider.getCurrentUserModel();

        if (limitService.violatesSharedUserPortalLimit(user, portal, newAccessUserId.size())) {
            throw new LimitException("Достигнут лимит на количество пользователей с доступом к порталу");
        }

        Set<Long> oldAccessUserId = portal.getAllowedUserIds() == null ? Set.of() : Set.copyOf(portal.getAllowedUserIds());
        portal.setAllowedUserIds(new HashSet<>(newAccessUserId));
        portalDomainService.savePortal(portal);
        logger.info("Пользователи {} успешно добавлены к порталу {}", newAccessUserId, portalId);

        portalHistoryService.record(portalId, PortalHistoryEvent.USERS_CHANGED, user.getId(), null, null, "users",
                joinUserIds(oldAccessUserId), joinUserIds(newAccessUserId));
    }

    public PortalSettingsResponse getPortalSettings(Long portalId) throws PortalException {
        Objects.requireNonNull(portalId, "portalId не должен быть null");

        PortalModel portal = portalDomainService.getPortalById(portalId);
        List<UserInfo> userInfoList = new ArrayList<>();

        Set<Long> portalUserIds = portal.getAllowedUserIds();
        for (Long userId : portalUserIds) {
            try {
                UserDetailsProjection user = userDomainService.getUserDetailsById(userId);
                UserInfo userInfo = new UserInfo(user.getId(), user.getFirstName(), user.getLastName(), user.getMiddleName(),
                        user.getEmail());

                userInfoList.add(userInfo);
            } catch (UsernameNotFoundException e) {
                UserInfo userInfo = new UserInfo(userId, "Пользователь не существует", "-", "-", "none@none.none");
                userInfoList.add(userInfo);
            }
        }

        return new PortalSettingsResponse(userInfoList, portal.isPublic());
    }

    /** История портала (передачи владения и изменения портала), новые записи сверху. Доступ: участники портала (см. контроллер). */
    public PageResponse<PortalHistoryResponse> getPortalHistory(Long portalId, int page, int size) {
        Objects.requireNonNull(portalId, "portalId не должен быть null");

        PageRequest pageRequest = paginationAdapter.buildPageRequest(page, size, "createdAt", "desc", Set.of("createdAt"));
        Page<PortalHistoryModel> historyPage = portalHistoryService.getPage(portalId, pageRequest);
        Page<PortalHistoryResponse> mappedPage = historyPage.map(this::toHistoryResponse);

        return paginationAdapter.mapToResponse(mappedPage, "createdAt", "desc");
    }

    private PortalHistoryResponse toHistoryResponse(PortalHistoryModel entry) {
        return new PortalHistoryResponse(entry.getId(), entry.getPortalId(), entry.getEvent().name(), entry.getActorId(),
                resolveUserName(entry.getActorId()), entry.getTargetUserId(), resolveUserName(entry.getTargetUserId()), entry.getReason(),
                entry.getFieldName(), entry.getOldValue(), entry.getNewValue(), entry.getCreatedAt());
    }

    /** Имя участника для истории: удалённый пользователь не должен ломать выдачу (точка входа — getPortalSettings). */
    private String resolveUserName(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            UserDetailsProjection user = userDomainService.getUserDetailsById(userId);
            String name = (user.getLastName() + " " + user.getFirstName()).trim();
            return name.isBlank() ? user.getEmail() : name;
        } catch (UsernameNotFoundException e) {
            return "Пользователь не существует";
        }
    }

    private String joinUserIds(Set<Long> userIds) {
        return userIds.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
    }

    @Transactional
    public DeleteResult deletePortals(Set<Long> portalIdSet) throws PortalException {
        Objects.requireNonNull(portalIdSet, "portalIdSet не должен быть null");
        Set<Long> deletedIds = ConcurrentHashMap.newKeySet();
        UserModel user = currentUserProvider.getCurrentUserModel();

        portalIdSet.parallelStream().forEach(id -> {
            try {
                if (accessValidationService.hasPortalOwner(id)) {
                    PortalModel portal = portalDomainService.getPortalById(id);
                    portal.setTimeDelete(Instant.now());
                    portal.setDeleted(true);
                    portalDomainService.savePortal(portal);
                    deletedIds.add(id);

                    portalHistoryService.record(id, PortalHistoryEvent.PORTAL_DELETED, user.getId(), null, null, "name", portal.getName(),
                            null);

                    // In-app оповещение об удалении портала (удаляющего publisher исключает)
                    notificationPublisher.publishToPortalUsers(portal, NotificationEvent.PORTAL_DELETED, user.getId(), null,
                            portal.getName());

                    // Отправляем письмо
                    emailDeliveryService.initNotifyPortalUsers(portal, emailTemplates.deletedPortalSubject(portal.getId()),
                            emailTemplates.deletedPortalBody(portal.getId(), user.getEmail()), NotificationEvent.PORTAL_DELETED);
                }
            } catch (Exception e) {
                logger.warn("Не удалось удалить портал с ID {}: {}", id, e.getMessage());
            }
        });

        return new DeleteResult(deletedIds.size(), deletedIds);
    }

    private List<PortalModel> getAccessiblePortals(Long userId) {
        return Stream
                .concat(portalDomainService.getPortalsByOwnerId(userId).stream(), portalDomainService.getAllSharedPortals(userId).stream())
                .toList();
    }

    public List<Long> getUserActivityInPublicPortals() {
        Long userId = currentUserProvider.getCurrentUserId();
        return portalDomainService.getPublicPortalsByUserActivity(userId);
    }

    public List<Long> mapAccessiblePortalsToIds() {
        Long userId = currentUserProvider.getCurrentUserModel().getId();
        return new ArrayList<>(getAccessiblePortals(userId).stream().map(PortalModel::getId).toList());
    }

    public List<PortalInfoResponse> mapAccessiblePortalsToInfo() {
        Long userId = currentUserProvider.getCurrentUserModel().getId();
        return getAccessiblePortals(userId).stream().map(p -> new PortalInfoResponse(p.getId(), p.getName())).toList();
    }

    public PortalInfoResponse getPortalInfo(Long portalId) throws PortalException {
        Objects.requireNonNull(portalId, "portalId не должен быть null");

        Long userId = currentUserProvider.getCurrentUserModel().getId();
        PortalModel portal = portalDomainService.getPortalById(portalId);
        return new PortalInfoResponse(portal.getId(), portal.getName(), portal.getDescription());
    }

    @Transactional
    public PortalInfoResponse updatePortalInfo(Long portalId, UpdatePortalInfoRequest request) throws PortalException {
        Objects.requireNonNull(request, "UpdatePortalInfoRequest не должен быть null");

        try {
            PortalModel portal = portalDomainService.getPortalById(portalId);
            String oldName = portal.getName();
            String oldDescription = portal.getDescription();

            portal.setName(request.name());
            portal.setDescription(request.description());

            portalDomainService.savePortal(portal);
            logger.info("Портал {} обновлён: имя — {}, описание — {}", portal.getId(), portal.getName(), portal.getDescription());

            Long actorId = currentUserProvider.getCurrentUserId();
            if (!Objects.equals(oldName, request.name())) {
                portalHistoryService.record(portalId, PortalHistoryEvent.NAME_CHANGED, actorId, null, null, "name", oldName,
                        request.name());
            }
            if (!Objects.equals(oldDescription, request.description())) {
                portalHistoryService.record(portalId, PortalHistoryEvent.DESCRIPTION_CHANGED, actorId, null, null, "description",
                        oldDescription, request.description());
            }

            return new PortalInfoResponse(portal.getId(), portal.getName(), portal.getDescription());
        } catch (OptimisticLockException e) {
            throw new PortalException("Данные портала были недавно изменены другим пользователем. "
                    + "Обновите страницу - получите актуальные данные и попробуйте снова.");
        }
    }
}
