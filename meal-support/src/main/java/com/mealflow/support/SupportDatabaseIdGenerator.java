package com.mealflow.support;

import com.mealflow.support.mapper.SupportSequenceMapper;
import org.springframework.stereotype.Component;

@Component
public class SupportDatabaseIdGenerator {
  private final SupportSequenceMapper supportSequenceMapper;

  public SupportDatabaseIdGenerator(SupportSequenceMapper supportSequenceMapper) {
    this.supportSequenceMapper = supportSequenceMapper;
  }

  public long next(String namespace) {
    Long current = supportSequenceMapper.lockValue(namespace);
    if (current == null) {
      throw new IllegalStateException("missing business sequence: " + namespace);
    }
    long next = current + 1;
    if (supportSequenceMapper.updateValue(namespace, next) != 1) {
      throw new IllegalStateException("failed to advance business sequence: " + namespace);
    }
    // The row lock keeps IDs unique across application instances for the whole transaction.
    return next;
  }
}
