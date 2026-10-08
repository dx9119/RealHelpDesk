package com.ukhanov.realhelpdesk.domain.portal.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.ukhanov.realhelpdesk.domain.portal.model.PortalHistoryEvent;
import com.ukhanov.realhelpdesk.domain.portal.model.PortalHistoryModel;
import com.ukhanov.realhelpdesk.domain.portal.repository.PortalHistoryRepository;

/**
 * Запись истории портала: передачи владения и изменения самого портала. Вызывается в той же транзакции, что и само изменение, поэтому
 * история не расходится с состоянием портала; обрезка длинных полей — по ограничениям колонок.
 */
@Service
public class PortalHistoryService {

    private static final int REASON_MAX_LENGTH = 2000;
    private static final int FIELD_NAME_MAX_LENGTH = 40;
    private static final int VALUE_MAX_LENGTH = 1000;

    private static final Logger logger = LoggerFactory.getLogger(PortalHistoryService.class);

    private final PortalHistoryRepository portalHistoryRepository;

    public PortalHistoryService(PortalHistoryRepository portalHistoryRepository) {
        this.portalHistoryRepository = portalHistoryRepository;
    }

    /** Системное событие (actorId = null) или действие пользователя: actorId — кто выполнил, targetUserId — второй участник. */
    public void record(Long portalId, PortalHistoryEvent event, Long actorId, Long targetUserId, String reason, String fieldName,
            String oldValue, String newValue) {
        PortalHistoryModel entry = new PortalHistoryModel();
        entry.setPortalId(portalId);
        entry.setEvent(event);
        entry.setActorId(actorId);
        entry.setTargetUserId(targetUserId);
        entry.setReason(truncate(reason, REASON_MAX_LENGTH));
        entry.setFieldName(truncate(fieldName, FIELD_NAME_MAX_LENGTH));
        entry.setOldValue(truncate(oldValue, VALUE_MAX_LENGTH));
        entry.setNewValue(truncate(newValue, VALUE_MAX_LENGTH));

        portalHistoryRepository.save(entry);
        logger.debug("История портала {}: {} (актор {})", portalId, event, actorId);
    }

    /** Записи истории портала, новые сверху. */
    public Page<PortalHistoryModel> getPage(Long portalId, Pageable pageable) {
        return portalHistoryRepository.findByPortalIdOrderByCreatedAtDesc(portalId, pageable);
    }

    /** Значение поля портала для истории: null не должен ронять запись. */
    public static String valueOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
