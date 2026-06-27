package com.mealflow.app.catalog;

import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import com.mealflow.common.status.StockReservationStatus;
import com.mealflow.infra.id.IdGenerator;
import com.mealflow.infra.idempotent.IdempotentTemplate;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {
  private final IdGenerator idGenerator;
  private final IdempotentTemplate idempotentTemplate;
  private final Map<Long, Sku> skus = new ConcurrentHashMap<>();
  private final Map<Long, StockReservation> reservations = new ConcurrentHashMap<>();

  public CatalogService(IdGenerator idGenerator, IdempotentTemplate idempotentTemplate) {
    this.idGenerator = idGenerator;
    this.idempotentTemplate = idempotentTemplate;
  }

  @PostConstruct
  void seed() {
    skus.put(1L, new Sku(1L, 10L, "招牌牛肉饭", 2800, 50));
    skus.put(2L, new Sku(2L, 10L, "香煎鸡腿饭", 2600, 50));
    skus.put(3L, new Sku(3L, 10L, "冰柠檬茶", 800, 100));
  }

  public List<SkuView> listByMerchant(long merchantId) {
    return skus.values().stream()
        .filter(sku -> sku.merchantId == merchantId)
        .sorted(Comparator.comparingLong(sku -> sku.id))
        .map(sku -> new SkuView(sku.id, sku.merchantId, sku.name, sku.priceCent, sku.stock))
        .toList();
  }

  public List<OrderItemSnapshot> buildSnapshots(long merchantId, List<OrderSkuItem> items) {
    return items.stream().map(item -> {
      Sku sku = findSku(item.skuId());
      if (sku.merchantId != merchantId) {
        throw new BizException(ErrorCode.BAD_REQUEST, "商品不属于当前商户");
      }
      return new OrderItemSnapshot(sku.id, sku.name, sku.priceCent, item.quantity());
    }).toList();
  }

  public List<Long> reserve(ReserveStockCommand command) {
    return idempotentTemplate.execute("stock-reserve:" + command.userId() + ":" + command.requestId(), () -> {
      List<Long> reservationIds = new ArrayList<>();
      synchronized (this) {
        for (OrderSkuItem item : command.items()) {
          Sku sku = findSku(item.skuId());
          if (sku.stock < item.quantity()) {
            throw new BizException(ErrorCode.STOCK_NOT_ENOUGH, sku.name + "库存不足");
          }
        }
        for (OrderSkuItem item : command.items()) {
          Sku sku = findSku(item.skuId());
          sku.stock -= item.quantity();
          long reservationId = idGenerator.next("stockReservation");
          StockReservation reservation = new StockReservation(
              reservationId,
              command.requestId(),
              command.userId(),
              command.merchantId(),
              item.skuId(),
              item.quantity(),
              StockReservationStatus.RESERVED,
              command.expireTime(),
              null,
              null
          );
          reservations.put(reservationId, reservation);
          reservationIds.add(reservationId);
        }
      }
      return reservationIds;
    });
  }

  public synchronized void bindToOrder(List<Long> reservationIds, long orderId) {
    reservationIds.forEach(id -> {
      StockReservation reservation = requireReservation(id);
      reservation.orderId = orderId;
    });
  }

  public synchronized void bindToTicket(List<Long> reservationIds, long ticketId) {
    reservationIds.forEach(id -> {
      StockReservation reservation = requireReservation(id);
      reservation.ticketId = ticketId;
    });
  }

  public synchronized void confirm(List<Long> reservationIds) {
    reservationIds.forEach(id -> {
      StockReservation reservation = requireReservation(id);
      if (reservation.status == StockReservationStatus.RESERVED) {
        reservation.status = StockReservationStatus.CONFIRMED;
      }
    });
  }

  public synchronized void release(List<Long> reservationIds) {
    reservationIds.forEach(id -> {
      StockReservation reservation = requireReservation(id);
      if (reservation.status == StockReservationStatus.RESERVED) {
        reservation.status = StockReservationStatus.RELEASED;
        Sku sku = findSku(reservation.skuId);
        sku.stock += reservation.quantity;
      }
    });
  }

  public List<StockReservationView> reservations() {
    return reservations.values().stream()
        .sorted(Comparator.comparingLong(reservation -> reservation.id))
        .map(reservation -> new StockReservationView(
            reservation.id,
            reservation.skuId,
            reservation.quantity,
            reservation.status.name(),
            reservation.ticketId,
            reservation.orderId
        ))
        .toList();
  }

  private Sku findSku(long skuId) {
    Sku sku = skus.get(skuId);
    if (sku == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "商品不存在");
    }
    return sku;
  }

  private StockReservation requireReservation(long reservationId) {
    StockReservation reservation = reservations.get(reservationId);
    if (reservation == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "库存预占不存在");
    }
    return reservation;
  }

  static class Sku {
    final long id;
    final long merchantId;
    final String name;
    final int priceCent;
    int stock;

    Sku(long id, long merchantId, String name, int priceCent, int stock) {
      this.id = id;
      this.merchantId = merchantId;
      this.name = name;
      this.priceCent = priceCent;
      this.stock = stock;
    }
  }

  static class StockReservation {
    final long id;
    final String requestId;
    final long userId;
    final long merchantId;
    final long skuId;
    final int quantity;
    StockReservationStatus status;
    final LocalDateTime expireTime;
    Long ticketId;
    Long orderId;

    StockReservation(long id, String requestId, long userId, long merchantId, long skuId, int quantity,
        StockReservationStatus status, LocalDateTime expireTime, Long ticketId, Long orderId) {
      this.id = id;
      this.requestId = requestId;
      this.userId = userId;
      this.merchantId = merchantId;
      this.skuId = skuId;
      this.quantity = quantity;
      this.status = status;
      this.expireTime = expireTime;
      this.ticketId = ticketId;
      this.orderId = orderId;
    }
  }
}
