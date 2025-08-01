package com.ukhanov.realhelpdesk.feature.portalmanager.service;

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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public PortalManageService(
        CurrentUserProvider currentUserProvider,
        PortalDomainService portalDomainService,
        PaginationService paginationService, PortalUtilsService portalUtilsService,
        AccessValidationService accessValidationService, LimitService limitService,
        UserDomainService userDomainService)
    {
        this.currentUserProvider = currentUserProvider;
        this.portalDomainService = portalDomainService;
        this.paginationService = paginationService;
        this.portalUtilsService = portalUtilsService;
        this.accessValidationService = accessValidationService;
      this.limitService = limitService;
      this.userDomainService = userDomainService;
    }

    public CreatePortalResponse createPortal(CreatePortalRequest request)
        throws PortalException, LimitException {
        logger.debug("Received portal creation request: {}", request);
        Objects.requireNonNull(request, "CreatePortalRequest must not be null");


        UserModel userModel = currentUserProvider.getCurrentUserModel();
        PortalModel portal = PortalMapper.toEntity(request, userModel);

        if(limitService.hasUserReachedPortalLimit(userModel)){
            throw new LimitException("User has reached portal limit");
        }

        if(portalDomainService.isPortalExistByName(portal.getOwner().getId(), portal.getName())) {
            throw new PortalException("Portal with name '"+portal.getName()+"' already exists");
        }

        portalDomainService.savePortal(portal);

        logger.info("Portal created for user {} with name '{}'", userModel.getId(), portal.getName());
        return new CreatePortalResponse("Portal create, id:"+portal.getId().toString());
    }

    public List<PortalResponse> getAllPortals() {
        logger.debug("Received request to get all portals");

        UserModel userModel = currentUserProvider.getCurrentUserModel();
        return portalDomainService.getPortalsByOwnerId(userModel.getId())
                .stream()
                .map(PortalMapper::toResponse)
                .toList();
    }

    public PageResponse<PortalResponse> getPagePortalsByOwner(int page, int size, String sortBy, String order) {
        logger.debug("Received request for paged portals — page: {}, size: {}, sortBy: {}, order: {}", page, size, sortBy, order);

        UserModel userModel = currentUserProvider.getCurrentUserModel();
        PageRequest pageRequest = paginationService.buildPageRequest(page, size, sortBy, order);

        Page<PortalModel> portalPage = portalDomainService.getPortalsPageByOwnerId(userModel.getId(), pageRequest);
        Page<PortalResponse> mappedPage = portalPage.map(PortalMapper::toResponse);

        return paginationService.mapToResponse(mappedPage, sortBy, order);
    }

    public PageResponse<PortalResponse> getPagePortalsByAccess(int page, int size, String sortBy, String order) {
        logger.debug("Received request for accessible portals — page: {}, size: {}, sortBy: {}, order: {}", page, size, sortBy, order);
        
        UserModel userModel = currentUserProvider.getCurrentUserModel();
        PageRequest pageRequest = paginationService.buildPageRequest(page, size, sortBy, order);

        Page<PortalModel> portalPage = portalDomainService.getPortalPageAccessByUser(userModel.getId(), pageRequest);
        Page<PortalResponse> mappedPage = portalPage.map(PortalMapper::toResponse);

        return paginationService.mapToResponse(mappedPage, sortBy, order);
    }

    public void setPortalStatus(Long portalId, boolean isPublic) throws PortalException {
        Objects.requireNonNull(portalId, "portalId must not be null");
        logger.debug("Received request to set portal {} status to {}", portalId, isPublic);
        PortalModel portal;
        try {
            portal = portalDomainService.getPortalById(portalId);
        } catch (IllegalArgumentException e) {
            throw new PortalException("Portal not found with ID: " + portalId, e);
        }
        portal.setPublic(isPublic);
        logger.info("Portal {} is now public: {}", portalId, isPublic);
        portalDomainService.savePortal(portal);
    }

    //todo добавить валидацию UUID
    public void addUserForPortal(Long portalId, Set<UUID> newAccessUserId)
        throws PortalException, LimitException {
        Objects.requireNonNull(portalId, "portalId must not be null");
        Objects.requireNonNull(newAccessUserId, "newAccessUserId must not be null");
        portalUtilsService.validateUUIDList(newAccessUserId);

        PortalModel portal = portalDomainService.getPortalById(portalId);
        UserModel user = currentUserProvider.getCurrentUserModel();

        if (limitService.violatesSharedUserPortalLimit(user, portal, newAccessUserId.size())) {
            throw new LimitException("The limit on the number of allowed portal users has been reached");
        }

        portal.setAllowedUserIds(new HashSet<>(newAccessUserId));
        portalDomainService.savePortal(portal);
        logger.info("Successfully set users {} for portal {}", newAccessUserId, portalId);
    }

    public PortalSettingsResponse getPortalSettings(Long portalId) throws PortalException {
        Objects.requireNonNull(portalId, "portalId must not be null");

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

    public DeleteResult deletePortals(Set<Long> portalIdSet) throws PortalException {
        Objects.requireNonNull(portalIdSet, "portalIdSet must not be null");

        Set<Long> deletedIds = new HashSet<>();

        for (Long id : portalIdSet) {
            try {
                if (accessValidationService.hasPortalOwner(id)) {
                    portalDomainService.deletePortalById(id);
                    deletedIds.add(id);
                }
            } catch (Exception e) {
                logger.debug("Failed to delete portal with id: {}", id, e);
            }
        }

        return new DeleteResult(deletedIds.size(), deletedIds);
    }

    private List<PortalModel> getAccessiblePortals(UUID userId) {
        return Stream.concat(
                portalDomainService.getPortalsByOwnerId(userId).stream(),
                portalDomainService.getAllSharedPortals(userId).stream()
            )
            .toList();
    }

    public List<Long> mapAccessiblePortalsToIds() {
        UUID userId = currentUserProvider.getCurrentUserModel().getId();
        return getAccessiblePortals(userId).stream()
            .map(PortalModel::getId)
            .toList();
    }

    public List<PortalInfoResponse> mapAccessiblePortalsToInfo() {
        UUID userId = currentUserProvider.getCurrentUserModel().getId();
        return getAccessiblePortals(userId).stream()
            .map(p -> new PortalInfoResponse(p.getId(), p.getName()))
            .toList();
    }



    public PortalInfoResponse getPortalInfo(Long portalId) throws PortalException {
        Objects.requireNonNull(portalId, "portalId must not be null");

        UUID userId = currentUserProvider.getCurrentUserModel().getId();
        PortalModel portal = portalDomainService.getPortalById(portalId);
        return new PortalInfoResponse(portal.getId(),portal.getName(),portal.getDescription());
    }

    public PortalInfoResponse updatePortalInfo(Long portalId, UpdatePortalInfoRequest request) throws PortalException {
        Objects.requireNonNull(request, "UpdatePortalInfoRequest must not be null");

        PortalModel portal = portalDomainService.getPortalById(portalId);
        portal.setName(request.getName());
        portal.setDescription(request.getDescription());

        portalDomainService.savePortal(portal);
        logger.info("Portal {} updated with name {} and description {},", portal.getId(), portal.getName(), portal.getDescription());
        return new PortalInfoResponse(portal.getId(),portal.getName(),portal.getDescription());
    }
}

