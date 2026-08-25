package com.mealflow.promotion.seckill;

public record SeckillClaimCommand(String eventKey, long voucherId, long userId) {
  public SeckillClaimCommand {
    String expected = eventKey(voucherId, userId);
    if (!expected.equals(eventKey)) {
      throw new IllegalArgumentException("invalid seckill event key");
    }
  }

  public static SeckillClaimCommand of(long voucherId, long userId) {
    return new SeckillClaimCommand(eventKey(voucherId, userId), voucherId, userId);
  }

  public static String eventKey(long voucherId, long userId) {
    return "seckill:" + voucherId + ":" + userId;
  }
}
