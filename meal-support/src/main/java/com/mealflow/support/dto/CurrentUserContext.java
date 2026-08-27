package com.mealflow.support.dto;

import java.util.List;

/** Identity of the authenticated principal, always sourced from gateway-injected headers. */
public record CurrentUserContext(long userId, String role, List<String> permissions, String traceId) {

  public boolean hasPermission(String permission) {
    return permissions != null && permissions.contains(permission);
  }
}
