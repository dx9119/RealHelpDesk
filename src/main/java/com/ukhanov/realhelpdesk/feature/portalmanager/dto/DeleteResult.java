package com.ukhanov.realhelpdesk.feature.portalmanager.dto;

import java.util.Collections;
import java.util.Set;

public class DeleteResult {
  private final int count;
  private final Set<Long> deletedIds;

  public DeleteResult(int count, Set<Long> deletedIds) {
    this.count = count;
    this.deletedIds = Collections.unmodifiableSet(deletedIds);
  }

  public int getCount() {
    return count;
  }

  public Set<Long> getDeletedIds() {
    return deletedIds;
  }
}

