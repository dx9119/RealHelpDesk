package com.ukhanov.realhelpdesk.feature.portalmanager.mapper;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.domain.portal.model.PortalModel;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.CreatePortalRequest;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.PortalResponse;

@Component
public class PortalMapper {

    public static PortalModel toEntity(CreatePortalRequest request, UserModel owner) {
        Objects.requireNonNull(request, "Запрос не должен быть null");
        Objects.requireNonNull(owner, "Владелец не должен быть null");

        PortalModel portal = new PortalModel();
        portal.setName(request.getName());
        portal.setDescription(request.getDescription());
        portal.setOwner(owner);
        return portal;
    }

    public static PortalResponse toResponse(PortalModel model) {
        return PortalResponse.builder().id(model.getId()).name(model.getName()).description(model.getDescription())
                .createdAt(model.getCreatedAt()).build();
    }

}
