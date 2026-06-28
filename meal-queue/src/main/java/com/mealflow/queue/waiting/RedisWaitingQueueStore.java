package com.mealflow.queue.waiting;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mealflow.queue", name = "waiting-store", havingValue = "redis")
public class RedisWaitingQueueStore implements WaitingQueueStore {
  private static final String KEY_PREFIX = "mealflow:queue:waiting:";

  private final StringRedisTemplate redisTemplate;
  private final LocalWaitingQueueStore fallback = new LocalWaitingQueueStore();

  public RedisWaitingQueueStore(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  @Override
  public void rebuild(Collection<WaitingTicketEntry> waitingTickets) {
    fallback.rebuild(waitingTickets);
    try {
      Set<String> staleKeys = redisTemplate.keys(KEY_PREFIX + "*");
      if (staleKeys != null && !staleKeys.isEmpty()) {
        redisTemplate.delete(staleKeys);
      }
    } catch (RuntimeException ignored) {
      // Local fallback has already been rebuilt from MySQL facts.
    }
    waitingTickets.stream().collect(Collectors.groupingBy(WaitingTicketEntry::merchantId))
        .forEach((merchantId, tickets) -> {
          try {
            String key = key(merchantId);
            tickets.forEach(ticket -> redisTemplate.opsForZSet().add(key, ticket.member(), ticket.score()));
          } catch (RuntimeException ignored) {
            fallback.rebuildForMerchant(merchantId, tickets);
          }
        });
  }

  @Override
  public void add(long merchantId, WaitingTicketEntry ticket) {
    fallback.add(merchantId, ticket);
    try {
      redisTemplate.opsForZSet().add(key(merchantId), ticket.member(), ticket.score());
    } catch (RuntimeException ignored) {
      // Local fallback already has the hot index.
    }
  }

  @Override
  public void remove(long merchantId, WaitingTicketEntry ticket) {
    fallback.remove(merchantId, ticket);
    try {
      redisTemplate.opsForZSet().remove(key(merchantId), ticket.member());
    } catch (RuntimeException ignored) {
      // Local fallback already removed the hot index entry.
    }
  }

  @Override
  public Optional<WaitingTicketEntry> poll(long merchantId) {
    try {
      Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().rangeWithScores(key(merchantId), 0, 0);
      if (tuples == null || tuples.isEmpty()) {
        return Optional.empty();
      }
      ZSetOperations.TypedTuple<String> tuple = tuples.iterator().next();
      String member = tuple.getValue();
      if (member == null || tuple.getScore() == null) {
        return Optional.empty();
      }
      redisTemplate.opsForZSet().remove(key(merchantId), member);
      fallback.poll(merchantId);
      return Optional.of(WaitingTicketEntry.fromMember(merchantId, member, tuple.getScore().longValue()));
    } catch (RuntimeException ex) {
      return fallback.poll(merchantId);
    }
  }

  @Override
  public int size(long merchantId) {
    try {
      Long size = redisTemplate.opsForZSet().zCard(key(merchantId));
      return size == null ? fallback.size(merchantId) : size.intValue();
    } catch (RuntimeException ex) {
      return fallback.size(merchantId);
    }
  }

  @Override
  public int rank(long merchantId, WaitingTicketEntry ticket, int fallbackRank) {
    try {
      Long rank = redisTemplate.opsForZSet().rank(key(merchantId), ticket.member());
      return rank == null ? fallback.rank(merchantId, ticket, fallbackRank) : rank.intValue();
    } catch (RuntimeException ex) {
      return fallback.rank(merchantId, ticket, fallbackRank);
    }
  }

  private String key(long merchantId) {
    return KEY_PREFIX + merchantId;
  }
}
