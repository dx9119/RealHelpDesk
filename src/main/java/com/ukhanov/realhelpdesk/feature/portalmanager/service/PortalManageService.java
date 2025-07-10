package com.ukhanov.realhelpdesk.feature.portalmanager.service;

import com.ukhanov.realhelpdesk.core.security.accesscontrol.AccessValidationService;
import com.ukhanov.realhelpdesk.core.security.user.CurrentUserProvider;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.domain.portal.model.PortalModel;
import com.ukhanov.realhelpdesk.domain.portal.service.PortalDomainService;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.CreatePortalRequest;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.CreatePortalResponse;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.PortalResponse;
import com.ukhanov.realhelpdesk.feature.portalmanager.exception.PortalException;
import com.ukhanov.realhelpdesk.feature.portalmanager.mapper.PortalMapper;
import com.ukhanov.realhelpdesk.feature.pagination.dto.PageResponse;
import com.ukhanov.realhelpdesk.feature.pagination.service.PaginationService;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class PortalManageService {

    private static final Logger logger = LoggerFactory.getLogger(PortalManageService.class);

    private final CurrentUserProvider currentUserProvider;
    private final PortalDomainService portalDomainService;
    private final PaginationService paginationService;
    private final AccessValidationService accessValidationService;

    public PortalManageService(
        CurrentUserProvider currentUserProvider,
        PortalDomainService portalDomainService,
        PaginationService paginationService, AccessValidationService accessValidationService)
    {
        this.currentUserProvider = currentUserProvider;
        this.portalDomainService = portalDomainService;
        this.paginationService = paginationService;
      this.accessValidationService = accessValidationService;
    }

    public CreatePortalResponse createPortal(CreatePortalRequest request) throws PortalException {
        logger.debug("Received portal creation request: {}", request);
        Objects.requireNonNull(request, "CreatePortalRequest must not be null");

        UserModel userModel = currentUserProvider.getCurrentUserModel();
        PortalModel portalModel = PortalMapper.toEntity(request, userModel);

        if(portalDomainService.isPortalExistByName(portalModel.getName())) {
            throw new PortalException("Portal with name '"+portalModel.getName()+"' already exists");
        }
        portalDomainService.savePortal(portalModel);

        logger.info("Portal created for user {} with name '{}'", userModel.getId(), portalModel.getName());
        return new CreatePortalResponse("Portal create, id:"+portalModel.getId().toString());
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

    public void grantUserAccessToPortal(Long portalId, List<UUID> newAccessUserId) throws PortalException {
        Objects.requireNonNull(portalId, "portalId must not be null");
        Objects.requireNonNull(newAccessUserId, "userId must not be null");


        if(!accessValidationService.hasPortalAccess(portalId)){
            throw new PortalException("Только создатель портала имеет право на изменения списка пользователей");
        }

        PortalModel portal = portalDomainService.getPortalById(portalId);
        for(UUID userId : newAccessUserId){
            if (portal.getAllowedUserIds().contains(userId)) {
                logger.debug("User {} already has access to portal {}", newAccessUserId, portalId);
                throw new PortalException("Пользователь уже имеет доступ к порталу");
            }

            if (!portal.getAllowedUserIds().add(userId)) {
                logger.debug("Failed to add user {} to portal {}", newAccessUserId, portalId);
                throw new PortalException("Не удалось предоставить пользователю доступ к порталу");
            }
        }

        logger.info("Granting user {} access to portal {}", newAccessUserId, portalId);
        portalDomainService.savePortal(portal);
    }

    public Set<UUID> getPortalUsers(Long portalId) {
        Objects.requireNonNull(portalId, "portalId must not be null");
        PortalModel portal = portalDomainService.getPortalById(portalId);
        return portal.getAllowedUserIds();
    }

}

