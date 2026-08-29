package com.mealflow.promotion.seckill;

import org.springframework.dao.DuplicateKeyException;

final class DuplicateClaimException extends RuntimeException {
  private final DuplicateKeyException duplicateCause;

  DuplicateClaimException(DuplicateKeyException cause) {
    super(cause);
    this.duplicateCause = cause;
  }

  DuplicateKeyException duplicateCause() {
    return duplicateCause;
  }
}
