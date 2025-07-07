package com.ukhanov.realhelpdesk.feature.portalmanager.mapper;

import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.domain.portal.model.PortalModel;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.CreatePortalRequest;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.PortalResponse;
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
