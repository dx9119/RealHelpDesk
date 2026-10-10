package com.ukhanov.realhelpdesk.feature.portalmanager.dto;

import java.util.Collections;
import java.util.Set;

public record DeleteResult(int count, Set<Long> deletedIds) {

    public DeleteResult {
        deletedIds = Collections.unmodifiableSet(deletedIds);
    }
}
