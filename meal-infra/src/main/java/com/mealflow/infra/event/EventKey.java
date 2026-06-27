package com.mealflow.infra.event;

public final class EventKey {
  private EventKey() {
  }

  public static String of(String producerService, String eventType, Object businessKey, int version) {
    return producerService + ":" + eventType + ":" + businessKey + ":" + version;
  }
}
