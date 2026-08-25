package com.mealflow.cart;

import org.springframework.stereotype.Component;

@Component
public class CartDatabaseIdGenerator {
  private final CartSequenceMapper sequenceMapper;
  public CartDatabaseIdGenerator(CartSequenceMapper sequenceMapper) { this.sequenceMapper = sequenceMapper; }
  public long next() {
    Long current = sequenceMapper.lockValue();
    if (current == null || sequenceMapper.updateValue(current + 1) != 1) throw new IllegalStateException("cart sequence unavailable");
    return current + 1;
  }
}
