package com.mealflow.order;

import com.mealflow.order.mapper.IdempotencyRecordMapper;
import com.mealflow.order.mapper.IdempotencyRecordRow;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Keeps request recovery state independent from the order business transaction. */
@Service
public class OrderIdempotencyRecordService {
  private final IdempotencyRecordMapper mapper;

  public OrderIdempotencyRecordService(IdempotencyRecordMapper mapper) {
    this.mapper = mapper;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean start(String subject, String key, String requestHash, LocalDateTime leaseExpireTime, LocalDateTime now) {
    return mapper.insertProcessing(subject, key, requestHash, leaseExpireTime, now) == 1;
  }

  @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
  public IdempotencyRecordRow find(String subject, String key) {
    return mapper.find(subject, key);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean takeOverExpired(String subject, String key, LocalDateTime leaseExpireTime, LocalDateTime now) {
    return mapper.takeOverExpired(subject, key, leaseExpireTime, now) == 1;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void complete(String subject, String key, String responseJson, LocalDateTime now) {
    mapper.complete(subject, key, responseJson, now);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markFailed(String subject, String key, LocalDateTime now) {
    mapper.markFailed(subject, key, now);
  }
}
