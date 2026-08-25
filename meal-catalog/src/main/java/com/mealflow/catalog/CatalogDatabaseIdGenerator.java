package com.mealflow.catalog;

import org.springframework.stereotype.Component;

@Component
public class CatalogDatabaseIdGenerator {
  private final CatalogBusinessSequenceMapper sequenceMapper;

  public CatalogDatabaseIdGenerator(CatalogBusinessSequenceMapper sequenceMapper) {
    this.sequenceMapper = sequenceMapper;
  }

  public long next(String namespace) {
    Long current = sequenceMapper.lockValue(namespace);
    if (current == null || sequenceMapper.updateValue(namespace, current + 1) != 1) {
      throw new IllegalStateException("failed to advance business sequence: " + namespace);
    }
    // Locking the sequence row makes the generated ID safe when catalog is deployed more than once.
    return current + 1;
  }
}
