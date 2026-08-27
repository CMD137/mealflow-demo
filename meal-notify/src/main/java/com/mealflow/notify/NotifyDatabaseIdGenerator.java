package com.mealflow.notify;

import com.mealflow.notify.mapper.NotifySequenceMapper;
import org.springframework.stereotype.Component;

@Component
public class NotifyDatabaseIdGenerator {
  private final NotifySequenceMapper mapper;

  public NotifyDatabaseIdGenerator(NotifySequenceMapper mapper) {
    this.mapper = mapper;
  }

  public long next(String namespace) {
    Long current = mapper.lockValue(namespace);
    if (current == null) {
      throw new IllegalStateException("missing notify sequence: " + namespace);
    }
    long next = current + 1;
    if (mapper.updateValue(namespace, next) != 1) {
      throw new IllegalStateException("failed to advance notify sequence: " + namespace);
    }
    return next;
  }
}
