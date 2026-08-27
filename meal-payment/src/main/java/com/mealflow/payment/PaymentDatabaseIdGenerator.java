package com.mealflow.payment;

import org.springframework.stereotype.Component;

@Component
public class PaymentDatabaseIdGenerator {
  private final PaymentBusinessSequenceMapper sequenceMapper;

  public PaymentDatabaseIdGenerator(PaymentBusinessSequenceMapper sequenceMapper) {
    this.sequenceMapper = sequenceMapper;
  }

  public long next(String namespace) {
    Long current = sequenceMapper.lockValue(namespace);
    if (current == null || sequenceMapper.updateValue(namespace, current + 1) != 1) {
      throw new IllegalStateException("failed to advance business sequence: " + namespace);
    }
    // Payment IDs are also used as callback correlation keys, so they must not repeat across nodes.
    return current + 1;
  }
}
