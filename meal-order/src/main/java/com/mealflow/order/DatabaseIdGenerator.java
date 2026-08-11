package com.mealflow.order;

import com.mealflow.order.mapper.BusinessSequenceMapper;
import org.springframework.stereotype.Component;

@Component
public class DatabaseIdGenerator {
  private final BusinessSequenceMapper businessSequenceMapper;

  public DatabaseIdGenerator(BusinessSequenceMapper businessSequenceMapper) {
    this.businessSequenceMapper = businessSequenceMapper;
  }

  public long next(String namespace) {
    Long current = businessSequenceMapper.lockValue(namespace);
    if (current == null) {
      throw new IllegalStateException("missing business sequence: " + namespace);
    }
    long next = current + 1;
    if (businessSequenceMapper.updateValue(namespace, next) != 1) {
      throw new IllegalStateException("failed to advance business sequence: " + namespace);
    }
    // The row lock keeps IDs unique across application instances for the whole transaction.
    return next;
  }
}
