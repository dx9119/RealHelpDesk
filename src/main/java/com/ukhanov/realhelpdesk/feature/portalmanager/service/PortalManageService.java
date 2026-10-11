package com.ukhanov.realhelpdesk.feature.portalmanager.service;

import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.mail.MessagingException;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.ukhanov.realhelpdesk.core.http.EntityTag;
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
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.PatchPortalInfoRequest;
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
    private static final Set<String> PORTAL_HISTORY_SORTABLE_FIELDS = Set.of("createdAt", "id");

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
    private final TransactionTemplate transactionTemplate;

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

        // Один батч-запрос по всем участникам вместо find по каждому (N+1)
        Set<Long> portalUserIds = portal.getAllowedUserIds();
        Map<Long, UserDetailsProjection> usersById = userDomainService.getUserDetailsByIds(portalUserIds);

        List<UserInfo> userInfoList = new ArrayList<>();
        for (Long userId : portalUserIds) {
            UserDetailsProjection user = usersById.get(userId);
            if (user != null) {
                userInfoList
                        .add(new UserInfo(user.getId(), user.getFirstName(), user.getLastName(), user.getMiddleName(), user.getEmail()));
            } else {
                logger.debug("Участник портала {} не найден в БД, подставлена заглушка", userId);
                userInfoList.add(new UserInfo(userId, "Пользователь не существует", "-", "-", "none@none.none"));
            }
        }

        return new PortalSettingsResponse(userInfoList, portal.isPublic());
    }

    /** История портала (передачи владения и изменения портала). Доступ: участники портала (см. контроллер). */
    public PageResponse<PortalHistoryResponse> getPortalHistory(Long portalId, int page, int size, String sortBy, String order) {
        Objects.requireNonNull(portalId, "portalId не должен быть null");

        PageRequest pageRequest = paginationAdapter.buildPageRequest(page, size, sortBy, order, PORTAL_HISTORY_SORTABLE_FIELDS);
        Page<PortalHistoryModel> historyPage = portalHistoryService.getPage(portalId, pageRequest);

        // Имена авторов и целей загружаются одним батчем на страницу, а не по запросу на запись истории
        Map<Long, String> displayNames = loadDisplayNames(historyPage);
        Page<PortalHistoryResponse> mappedPage = historyPage.map(entry -> toHistoryResponse(entry, displayNames));

        return paginationAdapter.mapToResponse(mappedPage);
    }

    private PortalHistoryResponse toHistoryResponse(PortalHistoryModel entry, Map<Long, String> displayNames) {
        return new PortalHistoryResponse(entry.getId(), entry.getPortalId(), entry.getEvent().name(), entry.getActorId(),
                entry.getActorId() == null ? null : displayNames.get(entry.getActorId()), entry.getTargetUserId(),
                entry.getTargetUserId() == null ? null : displayNames.get(entry.getTargetUserId()), entry.getReason(), entry.getFieldName(),
                entry.getOldValue(), entry.getNewValue(), entry.getCreatedAt());
    }

    /** Отображаемые имена участников записей страницы истории: удалённый пользователь не должен ломать выдачу. */
    private Map<Long, String> loadDisplayNames(Page<PortalHistoryModel> historyPage) {
        Set<Long> userIds = historyPage.getContent().stream().flatMap(entry -> Stream.of(entry.getActorId(), entry.getTargetUserId()))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, UserDetailsProjection> usersById = userDomainService.getUserDetailsByIds(userIds);
        Map<Long, String> displayNames = new HashMap<>();
        for (Long userId : userIds) {
            UserDetailsProjection user = usersById.get(userId);
            if (user == null) {
                logger.debug("Автор записи истории {} не найден, имя заменено", userId);
                displayNames.put(userId, "Пользователь не существует");
            } else {
                String name = (user.getLastName() + " " + user.getFirstName()).trim();
                displayNames.put(userId, name.isBlank() ? user.getEmail() : name);
            }
        }
        return displayNames;
    }

    private String joinUserIds(Set<Long> userIds) {
        return userIds.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
    }

    public DeleteResult deletePortals(Set<Long> portalIdSet) throws PortalException {
        Objects.requireNonNull(portalIdSet, "portalIdSet не должен быть null");
        UserModel user = currentUserProvider.getCurrentUserModel();

        // Последовательно и по одной транзакции на портал: удаление одного портал не откатывает,
        // письма/оповещения не держат открытую транзакцию (раньше — параллельный стрим с SMTP внутри tx)
        Set<Long> deletedIds = new LinkedHashSet<>();
        List<PortalModel> deletedPortals = new ArrayList<>();
        for (Long id : portalIdSet) {
            PortalModel portal = deletePortalInOwnTransaction(id, user);
            if (portal != null) {
                deletedIds.add(id);
                deletedPortals.add(portal);
            }
        }

        // Оповещения и письма — после коммита всех удалений: единый фолдаут без транзакционного контекста
        for (PortalModel portal : deletedPortals) {
            try {
                // In-app оповещение об удалении портала (удаляющего publisher исключает)
                notificationPublisher.publishToPortalUsers(portal, NotificationEvent.PORTAL_DELETED, user.getId(), null, portal.getName());

                // Отправляем письмо
                emailDeliveryService.initNotifyPortalUsers(portal, emailTemplates.deletedPortalSubject(portal.getId()),
                        emailTemplates.deletedPortalBody(portal.getId(), user.getEmail()), NotificationEvent.PORTAL_DELETED);
            } catch (Exception e) {
                logger.warn("Не удалось уведомить о удалении портала {}: {}", portal.getId(), e.getMessage());
            }
        }

        logger.info("Массовое удаление порталов: запрошено={}, удалено={}, userId={}", portalIdSet.size(), deletedIds.size(), user.getId());
        return new DeleteResult(deletedIds.size(), Set.copyOf(deletedIds));
    }

    /** Мягкое удаление одного портала в собственной транзакции; null — нет прав владельца или ошибка. */
    private PortalModel deletePortalInOwnTransaction(Long portalId, UserModel user) {
        try {
            return transactionTemplate.execute(status -> {
                try {
                    if (!accessValidationService.hasPortalOwner(portalId)) {
                        return null;
                    }
                    PortalModel portal = portalDomainService.getPortalById(portalId);
                    portal.setTimeDelete(Instant.now());
                    portal.setDeleted(true);
                    portalDomainService.savePortal(portal);

                    portalHistoryService.record(portalId, PortalHistoryEvent.PORTAL_DELETED, user.getId(), null, null, "name",
                            portal.getName(), null);
                    return portal;
                } catch (PortalException e) {
                    // checked-исключение не может выйти из TransactionCallback: оборачиваем, откат — снаружи
                    throw new IllegalStateException(e);
                }
            });
        } catch (Exception e) {
            // распакованная причина важнее обёртки: сообщение для оператора должно быть исходным
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            logger.warn("Не удалось удалить портал с ID {}: {}", portalId, cause.getMessage());
            return null;
        }
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

        PortalModel portal = portalDomainService.getPortalById(portalId);
        return toInfoResponse(portal);
    }

    @Transactional
    public PortalInfoResponse updatePortalInfo(Long portalId, UpdatePortalInfoRequest request, String ifMatch) throws PortalException {
        Objects.requireNonNull(request, "UpdatePortalInfoRequest не должен быть null");

        try {
            PortalModel portal = portalDomainService.getPortalById(portalId);
            requireFreshVersion(portal, ifMatch);

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

            return toInfoResponse(portal);
        } catch (OptimisticLockException e) {
            throw new PortalException("Данные портала были недавно изменены другим пользователем. "
                    + "Обновите страницу - получите актуальные данные и попробуйте снова.");
        }
    }

    /**
     * Частичное обновление: меняются только переданные поля. Полная замена — {@code PUT}: там клиент обязан прислать ресурс целиком, а
     * здесь достаточно одного поля.
     */
    @Transactional
    public PortalInfoResponse patchPortalInfo(Long portalId, PatchPortalInfoRequest request, String ifMatch) throws PortalException {
        Objects.requireNonNull(request, "PatchPortalInfoRequest не должен быть null");
        if (request.isEmpty()) {
            throw new PortalException("Не передано ни одного поля для изменения", HttpStatus.BAD_REQUEST);
        }

        try {
            PortalModel portal = portalDomainService.getPortalById(portalId);
            requireFreshVersion(portal, ifMatch);

            Long actorId = currentUserProvider.getCurrentUserId();
            if (request.name() != null && !Objects.equals(portal.getName(), request.name())) {
                String oldName = portal.getName();
                portal.setName(request.name());
                portalHistoryService.record(portalId, PortalHistoryEvent.NAME_CHANGED, actorId, null, null, "name", oldName,
                        request.name());
            }
            if (request.description() != null && !Objects.equals(portal.getDescription(), request.description())) {
                String oldDescription = portal.getDescription();
                portal.setDescription(request.description());
                portalHistoryService.record(portalId, PortalHistoryEvent.DESCRIPTION_CHANGED, actorId, null, null, "description",
                        oldDescription, request.description());
            }

            portalDomainService.savePortal(portal);
            logger.info("Портал {} обновлён частично (PATCH)", portalId);
            return toInfoResponse(portal);
        } catch (OptimisticLockException e) {
            throw new PortalException("Данные портала были недавно изменены другим пользователем. "
                    + "Обновите страницу - получите актуальные данные и попробуйте снова.");
        }
    }

    private PortalInfoResponse toInfoResponse(PortalModel portal) {
        return new PortalInfoResponse(portal.getId(), portal.getName(), portal.getDescription(), portal.getVersion());
    }

    /** If-Match: клиент присылает версию, которую видел; несовпадение — 412 до записи, а не 409 после. */
    private void requireFreshVersion(PortalModel portal, String ifMatch) throws PortalException {
        if (!EntityTag.satisfiedBy(ifMatch, EntityTag.of("portal", portal.getVersion()), portal.getVersion())) {
            throw new PortalException("Портал уже изменён: данные устарели, обновите страницу и повторите", HttpStatus.PRECONDITION_FAILED);
        }
    }
}
