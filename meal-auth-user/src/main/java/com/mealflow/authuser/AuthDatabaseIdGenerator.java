package com.mealflow.authuser;

import org.springframework.stereotype.Component;

@Component
public class AuthDatabaseIdGenerator {
  private final AuthBusinessSequenceMapper sequenceMapper;

  public AuthDatabaseIdGenerator(AuthBusinessSequenceMapper sequenceMapper) {
    this.sequenceMapper = sequenceMapper;
  }

  public long next(String namespace) {
    Long current = sequenceMapper.lockValue(namespace);
    if (current == null || sequenceMapper.updateValue(namespace, current + 1) != 1) {
      throw new IllegalStateException("failed to advance business sequence: " + namespace);
    }
    // User and employee IDs must remain unique when login requests hit different service instances.
    return current + 1;
  }
}
