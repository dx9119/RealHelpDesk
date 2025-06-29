package com.ukhanov.RealHelpDesk.feature.portalmanager.service;

import com.ukhanov.RealHelpDesk.core.security.user.CurrentUserProvider;
import com.ukhanov.RealHelpDesk.core.security.user.model.UserModel;
import com.ukhanov.RealHelpDesk.domain.portal.model.PortalModel;
import com.ukhanov.RealHelpDesk.domain.portal.service.PortalDomainService;
import com.ukhanov.RealHelpDesk.feature.portalmanager.dto.CreatePortalRequest;
import com.ukhanov.RealHelpDesk.feature.portalmanager.dto.CreatePortalResponse;
import com.ukhanov.RealHelpDesk.feature.portalmanager.dto.PortalResponse;
import com.ukhanov.RealHelpDesk.feature.portalmanager.exception.PortalException;
import com.ukhanov.RealHelpDesk.feature.portalmanager.mapper.PortalMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class PortalManageService {

    private static final Logger logger = LoggerFactory.getLogger(PortalManageService.class);

    private final CurrentUserProvider currentUserProvider;
    private final PortalMapper portalMapper;
    private final PortalDomainService portalDomainService;

    public PortalManageService(CurrentUserProvider currentUserProvider, PortalMapper portalMapper, PortalDomainService portalDomainService) {
        this.currentUserProvider = currentUserProvider;
        this.portalMapper = portalMapper;
        this.portalDomainService = portalDomainService;
    }

    public CreatePortalResponse createPortal(CreatePortalRequest request) throws PortalException {
        logger.debug("Received portal creation request: {}", request);
        Objects.requireNonNull(request, "CreatePortalRequest must not be null");

        UserModel userModel = currentUserProvider.getCurrentUserModel();
        PortalModel portalModel = portalMapper.toEntity(request, userModel);

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
                .map(portalMapper::toResponse)
                .toList();
    }

}

