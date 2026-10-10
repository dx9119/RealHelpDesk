package com.ukhanov.realhelpdesk.feature.usermanager.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.feature.usermanager.dto.UserInfoRequest;
import com.ukhanov.realhelpdesk.feature.usermanager.dto.UserInfoResponse;

/** Маппинг профиля пользователя: в ответ уходят только публичные поля, служебные (пароль, токены) не вытекают. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    UserInfoResponse toResponse(UserModel userModel);

    /** Обновление профиля трогает только ФИО и доп. инфо: почта, пароль и статусы меняются отдельными потоками. */
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "firstName", source = "request.firstName")
    @Mapping(target = "lastName", source = "request.lastName")
    @Mapping(target = "middleName", source = "request.middleName")
    @Mapping(target = "additionalInfo", source = "request.additionalInfo")
    UserModel toModel(UserInfoRequest request);

}
