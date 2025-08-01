package com.ukhanov.realhelpdesk.core.security.limiter.service;

import com.ukhanov.realhelpdesk.core.security.user.model.UserModel;
import com.ukhanov.realhelpdesk.domain.portal.model.PortalModel;
import com.ukhanov.realhelpdesk.domain.portal.repository.PortalRepository;
import org.springframework.stereotype.Service;

@Service
public class LimitService {
  private final PortalRepository portalRepository;

  public LimitService(PortalRepository portalRepository) {
    this.portalRepository = portalRepository;
  }

  public boolean hasUserReachedPortalLimit(UserModel user) {
    Integer userPortalLimit = user.getPortalCuntLimit();
    Integer currentUserPortalCount = portalRepository.countPortalByOwnerId(user.getId());
    return currentUserPortalCount >= userPortalLimit;
  }

  public boolean violatesSharedUserPortalLimit(UserModel user, PortalModel portal, int newUserCount) {
    Integer limit = user.getPortalSharedUsersCountLimit();
    if (limit == null) return false;

    int currentCount = portalRepository.countAllowedUsersByPortalId(portal.getId());

    // если пользователь хочет уменьшить количество — разрешаем
    if (newUserCount <= currentCount) {
      return false;
    }

    // иначе проверяем лимит
    return newUserCount > limit;
  }
}
