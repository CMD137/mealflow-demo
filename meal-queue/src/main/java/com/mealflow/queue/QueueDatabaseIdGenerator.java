package com.mealflow.queue;

import org.springframework.stereotype.Component;

@Component
public class QueueDatabaseIdGenerator {
  private final QueueBusinessSequenceMapper sequenceMapper;

  public QueueDatabaseIdGenerator(QueueBusinessSequenceMapper sequenceMapper) {
    this.sequenceMapper = sequenceMapper;
  }

  public long next(String namespace) {
    Long current = sequenceMapper.lockValue(namespace);
    if (current == null || sequenceMapper.updateValue(namespace, current + 1) != 1) {
      throw new IllegalStateException("failed to advance business sequence: " + namespace);
    }
    // Capacity and ticket IDs share this database lock, so multiple workers cannot collide.
    return current + 1;
  }
}
