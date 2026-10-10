package com.ukhanov.realhelpdesk.feature.portalmanager.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.domain.portal.model.PortalModel;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.CreatePortalRequest;
import com.ukhanov.realhelpdesk.feature.portalmanager.dto.PortalResponse;

/** Маппинг порталов: владелец приходит отдельным параметром — его нельзя выставить из запроса создания. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PortalMapper {

    /** Копируются имя, описание и владелец: id, createdAt и версию в новой entity проставит JPA. */
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "name", source = "request.name")
    @Mapping(target = "description", source = "request.description")
    @Mapping(target = "owner", source = "owner")
    PortalModel toEntity(CreatePortalRequest request, UserModel owner);

    PortalResponse toResponse(PortalModel model);

}
