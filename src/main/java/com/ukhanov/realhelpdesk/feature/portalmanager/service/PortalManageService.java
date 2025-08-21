package com.ukhanov.realhelpdesk.feature.portalmanager.service;

import com.ukhanov.realhelpdesk.core.mail.model.EmailTemplates;
import com.ukhanov.realhelpdesk.core.mail.model.NotificationEvent;
import com.ukhanov.realhelpdesk.core.mail.service.EmailDeliveryService;
import com.ukhanov.realhelpdesk.core.security.accesscontrol.AccessValidationService;
import com.ukhanov.realhelpdesk.core.security.limiter.exception.LimitException;
import com.ukhanov.realhelpdesk.core.security.limiter.service.LimitService;
import com.ukhanov.realhelpdesk.core.security.user.CurrentUserProvider;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.core.security.user.repository.UserDetailsProjection;
import com.ukhanov.realhelpdesk.core.security.user.service.UserDomainService;
import com.ukhanov.realhelpdesk.domain.portal.model.PortalModel;
import com.ukhanov.realhelpdesk.domain.portal.service.PortalDomainService;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.CreatePortalRequest;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.CreatePortalResponse;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.DeleteResult;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.PortalInfoResponse;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.PortalResponse;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.PortalSettingsResponse;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.UpdatePortalInfoRequest;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.UserInfo;
import com.ukhanov.realhelpdesk.feature.portalmanager.exception.PortalException;
import com.ukhanov.realhelpdesk.feature.portalmanager.mapper.PortalMapper;
import com.ukhanov.realhelpdesk.core.pagination.dto.PageResponse;
import com.ukhanov.realhelpdesk.core.pagination.service.PaginationService;

import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import com.ukhanov.realhelpdesk.feature.usermanager.exception.UserManageException;
import jakarta.mail.MessagingException;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class PortalManageService {

    private static final Logger logger = LoggerFactory.getLogger(PortalManageService.class);


    private final CurrentUserProvider currentUserProvider;
    private final PortalDomainService portalDomainService;
    private final PaginationService paginationService;
    private final PortalUtilsService portalUtilsService;
    private final AccessValidationService accessValidationService;
    private final LimitService limitService;
    private final UserDomainService userDomainService;
    private final EmailDeliveryService emailDeliveryService;

    public PortalManageService(
            CurrentUserProvider currentUserProvider,
            PortalDomainService portalDomainService,
            PaginationService paginationService, PortalUtilsService portalUtilsService,
            AccessValidationService accessValidationService, LimitService limitService,
            UserDomainService userDomainService, EmailDeliveryService emailDeliveryService)
    {
        this.currentUserProvider = currentUserProvider;
        this.portalDomainService = portalDomainService;
        this.paginationService = paginationService;
        this.portalUtilsService = portalUtilsService;
        this.accessValidationService = accessValidationService;
      this.limitService = limitService;
      this.userDomainService = userDomainService;
        this.emailDeliveryService = emailDeliveryService;
    }

    public CreatePortalResponse createPortal(CreatePortalRequest request)
            throws PortalException, LimitException, UserManageException, MessagingException, UnsupportedEncodingException {
        logger.debug("Получен запрос на создание портала: {}", request);
        Objects.requireNonNull(request, "Запрос на создание портала не должен быть null");


        UserModel userModel = currentUserProvider.getCurrentUserModel();
        PortalModel portal = PortalMapper.toEntity(request, userModel);

        if(!userModel.isEmailVerified()){
            throw new UserManageException("Подтвердите ваш адрес электронной почты, далее вы сможете создать портал");
        }

        if(limitService.hasUserReachedPortalLimit(userModel)){
            throw new LimitException("Пользователь достиг лимита на количество порталов");
        }

        if(portalDomainService.isPortalExistByName(portal.getOwner().getId(), portal.getName())) {
            throw new PortalException("Портал с именем '" + portal.getName() + "' уже существует");
        }

        // Тут портал получил ID
        PortalModel savePortal = portalDomainService.savePortal(portal);

        logger.info("Портал создан для пользователя {} с именем '{}'", userModel.getId(), portal.getName());

        // Отправляем письмо
        emailDeliveryService.initNotifyPortalUsers(
                portal,
                EmailTemplates.portalCreatedSubject(savePortal.getId()),
                EmailTemplates.portalCreatedBody(savePortal.getId()),
                NotificationEvent.NEW_PORTAL
        );

        return new CreatePortalResponse("Портал создан, id:"+savePortal.getId().toString());
    }

    public List<PortalResponse> getAllPortals() {
        logger.debug("Получен запрос на получение всех порталов");

        UserModel userModel = currentUserProvider.getCurrentUserModel();
        return portalDomainService.getPortalsByOwnerId(userModel.getId())
                .stream()
                .map(PortalMapper::toResponse)
                .toList();
    }

    public List<Long> getAllPortalIds() {
        logger.debug("Получен запрос на получение всех порталов и отображение их ID");

        UserModel userModel = currentUserProvider.getCurrentUserModel();
        return portalDomainService.getPortalsByOwnerId(userModel.getId())
                .stream()
                .map(PortalModel::getId)
                .toList();
    }

    public PageResponse<PortalResponse> getPagePortalsByOwner(int page, int size, String sortBy, String order) {
        UserModel userModel = currentUserProvider.getCurrentUserModel();
        PageRequest pageRequest = paginationService.buildPageRequest(page, size, sortBy, order);

        Page<PortalModel> portalPage = portalDomainService.getPortalsPageByOwnerId(userModel.getId(), pageRequest);
        Page<PortalResponse> mappedPage = portalPage.map(PortalMapper::toResponse);

        return paginationService.mapToResponse(mappedPage, sortBy, order);
    }

    public PageResponse<PortalResponse> getPagePortalsByAccess(int page, int size, String sortBy, String order) {
        UserModel userModel = currentUserProvider.getCurrentUserModel();
        PageRequest pageRequest = paginationService.buildPageRequest(page, size, sortBy, order);

        Page<PortalModel> portalPage = portalDomainService.getPortalPageAccessByUser(userModel.getId(), pageRequest);
        Page<PortalResponse> mappedPage = portalPage.map(PortalMapper::toResponse);

        return paginationService.mapToResponse(mappedPage, sortBy, order);
    }

    public void setPortalStatus(Long portalId, boolean isPublic) throws PortalException {
        Objects.requireNonNull(portalId, "portalId не должен быть null");
        logger.debug("Получен запрос на изменение статуса портала {} на {}", portalId, isPublic);

        PortalModel portal;
        try {
            portal = portalDomainService.getPortalById(portalId);
        } catch (IllegalArgumentException e) {
            throw new PortalException("Портал с ID " + portalId + " не найден", e);
        }
        portal.setPublic(isPublic);
        portalDomainService.savePortal(portal);
    }

    public Boolean getStatusPortal(Long portalId) throws PortalException {
        PortalModel portalModel = portalDomainService.getPortalById(portalId);
        return portalModel.isPublic();
    }

    //todo добавить валидацию UUID
    public void addUserForPortal(Long portalId, Set<UUID> newAccessUserId)
        throws PortalException, LimitException {
        Objects.requireNonNull(portalId, "portalId не должен быть null");
        Objects.requireNonNull(newAccessUserId, "newAccessUserId не должен быть null");

        portalUtilsService.validateUUIDList(newAccessUserId);

        PortalModel portal = portalDomainService.getPortalById(portalId);
        UserModel user = currentUserProvider.getCurrentUserModel();

        if (limitService.violatesSharedUserPortalLimit(user, portal, newAccessUserId.size())) {
            throw new LimitException("Достигнут лимит на количество пользователей с доступом к порталу");
        }

        portal.setAllowedUserIds(new HashSet<>(newAccessUserId));
        portalDomainService.savePortal(portal);
        logger.info("Пользователи {} успешно добавлены к порталу {}", newAccessUserId, portalId);
    }

    public PortalSettingsResponse getPortalSettings(Long portalId) throws PortalException {
        Objects.requireNonNull(portalId, "portalId не должен быть null");

        PortalModel portal = portalDomainService.getPortalById(portalId);
        List<UserInfo> userInfoList = new ArrayList<>();

        Set<UUID> portalUserIds = portal.getAllowedUserIds();
        for (UUID userId : portalUserIds) {
            try {
                UserDetailsProjection user = userDomainService.getUserDetailsById(userId);
                UserInfo userInfo = new UserInfo();
                userInfo.setId(user.getId());
                userInfo.setFirstName(user.getFirstName());
                userInfo.setLastName(user.getLastName());
                userInfo.setMiddleName(user.getMiddleName());
                userInfo.setEmail(user.getEmail());

                userInfoList.add(userInfo);
            } catch (UsernameNotFoundException e){
                UserInfo userInfo = new UserInfo();
                userInfo.setId(userId);
                userInfo.setFirstName("Пользователь не существует");
                userInfo.setLastName("-");
                userInfo.setMiddleName("-");
                userInfo.setEmail("none@none.none");
                userInfoList.add(userInfo);
            }
        }

      return new PortalSettingsResponse(
          userInfoList,
          portal.isPublic()
      );
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

                    // Отправляем письмо
                    emailDeliveryService.initNotifyPortalUsers(
                            portal,
                            EmailTemplates.deletedPortalSubject(portal.getId()),
                            EmailTemplates.deletedPortalBody(portal.getId(), user.getEmail()),
                            NotificationEvent.PORTAL_DELETED
                    );
                }
            } catch (Exception e) {
                logger.debug("Не удалось удалить портал с ID: {}", id, e);
            }
        });

        return new DeleteResult(deletedIds.size(), deletedIds);
    }


    private List<PortalModel> getAccessiblePortals(UUID userId) {
        return Stream.concat(
                portalDomainService.getPortalsByOwnerId(userId).stream(),
                portalDomainService.getAllSharedPortals(userId).stream()
            )
            .toList();
    }

    public List<Long> getUserActivityInPublicPortals() {
        UUID userId = currentUserProvider.getCurrentUserId();
        return portalDomainService.getPublicPortalsByUserActivity(userId);
    }

    public List<Long> mapAccessiblePortalsToIds() {
        UUID userId = currentUserProvider.getCurrentUserModel().getId();
        return new ArrayList<>(
                getAccessiblePortals(userId).stream()
                        .map(PortalModel::getId)
                        .toList()
        );
    }


    public List<PortalInfoResponse> mapAccessiblePortalsToInfo() {
        UUID userId = currentUserProvider.getCurrentUserModel().getId();
        return getAccessiblePortals(userId).stream()
            .map(p -> new PortalInfoResponse(p.getId(), p.getName()))
            .toList();
    }



    public PortalInfoResponse getPortalInfo(Long portalId) throws PortalException {
        Objects.requireNonNull(portalId, "portalId не должен быть null");

        UUID userId = currentUserProvider.getCurrentUserModel().getId();
        PortalModel portal = portalDomainService.getPortalById(portalId);
        return new PortalInfoResponse(portal.getId(),portal.getName(),portal.getDescription());
    }


    @Transactional
    public PortalInfoResponse updatePortalInfo(Long portalId, UpdatePortalInfoRequest request) throws PortalException {
        Objects.requireNonNull(request, "UpdatePortalInfoRequest не должен быть null");

        try {
            PortalModel portal = portalDomainService.getPortalById(portalId);
            portal.setName(request.getName());
            portal.setDescription(request.getDescription());

            portalDomainService.savePortal(portal);
            logger.info("Портал {} обновлён: имя — {}, описание — {}", portal.getId(), portal.getName(), portal.getDescription());
            return new PortalInfoResponse(portal.getId(), portal.getName(), portal.getDescription());
        } catch (OptimisticLockException e){
            logger.warn("Конфликт при обновлении портала {}: данные уже были изменены другим пользователем", portalId);
            throw new PortalException("Данные портала были недавно изменены другим пользователем. Обновите страницу - получите актуальные данные и попробуйте снова.");
        }
    }
}

