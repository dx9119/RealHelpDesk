package com.ukhanov.RealHelpDesk.feature.portalmanager.mapper;

import com.ukhanov.RealHelpDesk.core.security.user.model.UserModel;
import com.ukhanov.RealHelpDesk.domain.portal.model.PortalModel;
import com.ukhanov.RealHelpDesk.feature.portalmanager.dto.CreatePortalRequest;
import com.ukhanov.RealHelpDesk.feature.portalmanager.dto.PortalResponse;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class PortalMapper {

    public PortalModel toEntity(CreatePortalRequest request, UserModel owner) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(owner, "owner must not be null");

        PortalModel portal = new PortalModel();
        portal.setName(request.getName());
        portal.setDescription(request.getDescription());
        portal.setOwner(owner);
        return portal;
    }

    public PortalResponse toResponse(PortalModel model) {
        return PortalResponse.builder()
                .id(model.getId())
                .name(model.getName())
                .description(model.getDescription())
                .createdAt(model.getCreatedAt())
                .build();
    }

}
