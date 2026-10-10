package com.ukhanov.realhelpdesk.core.security.auth.mapper;

import java.security.SecureRandom;

import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

import com.ukhanov.realhelpdesk.core.security.auth.register.dto.RegisterRequest;
import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;

/** Маппинг регистрации: {@code passwordHash} приходит отдельным параметром, токен подтверждения почты генерируется после маппинга. */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public abstract class AuthMapper {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Копируются только поля из запроса плюс уже посчитанный хеш пароля; остальное (id, роль, токены) задаёт сервис и БД. */
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "firstName", source = "request.firstName")
    @Mapping(target = "lastName", source = "request.lastName")
    @Mapping(target = "email", source = "request.email")
    @Mapping(target = "externalId", source = "request.externalId")
    @Mapping(target = "passwordHash", source = "passwordHash")
    @Mapping(target = "userExternalSource", source = "request.userPlatformSource")
    public abstract UserModel toEntity(RegisterRequest request, String passwordHash);

    /** Токен подтверждения почты — случайное значение, его нельзя взять из запроса. */
    @AfterMapping
    protected void fillVerifyEmailToken(@MappingTarget UserModel user) {
        user.setVerifyEmailToken(RANDOM.nextLong());
    }

}
