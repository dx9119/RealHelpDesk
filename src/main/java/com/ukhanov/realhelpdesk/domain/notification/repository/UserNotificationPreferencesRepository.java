package com.ukhanov.realhelpdesk.domain.notification.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ukhanov.realhelpdesk.domain.notification.model.UserNotificationPreferencesModel;

@Repository
public interface UserNotificationPreferencesRepository extends JpaRepository<UserNotificationPreferencesModel, Long> {

    Optional<UserNotificationPreferencesModel> findByUserId(Long userId);

    List<UserNotificationPreferencesModel> findByUserIdIn(Set<Long> userIds);

    void deleteByUserId(Long userId);
}
