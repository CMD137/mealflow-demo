package com.mealflow.promotion.mq;

import com.mealflow.promotion.seckill.SeckillClaimCommand;

public interface SeckillClaimPublisher {
  void publish(SeckillClaimCommand command);
}
