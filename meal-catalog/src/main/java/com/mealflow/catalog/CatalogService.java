package com.mealflow.catalog;

import com.mealflow.catalog.api.OrderItemSnapshot;
import com.mealflow.catalog.api.OrderSkuItem;
import com.mealflow.catalog.api.ReserveStockRequest;
import com.mealflow.catalog.api.ReserveStockResponse;
import com.mealflow.catalog.api.SkuView;
import com.mealflow.catalog.api.StockReservationView;
import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import com.mealflow.common.status.StockReservationStatus;
import com.mealflow.infra.id.IdGenerator;
import com.mealflow.infra.idempotent.IdempotentTemplate;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
  private final JdbcTemplate jdbcTemplate;
  private final IdGenerator idGenerator = new IdGenerator();
  private final IdempotentTemplate idempotentTemplate = new IdempotentTemplate();

  public CatalogService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<SkuView> listByMerchant(long merchantId) {
    return jdbcTemplate.query(
        "SELECT id, merchant_id, name, price_cent, stock FROM sku WHERE merchant_id = ? ORDER BY id",
        this::mapSku,
        merchantId
    );
  }

  public List<OrderItemSnapshot> buildSnapshots(long merchantId, List<OrderSkuItem> items) {
    return items.stream().map(item -> {
      SkuView sku = findSku(item.skuId());
      if (sku.merchantId() != merchantId) {
        throw new BizException(ErrorCode.BAD_REQUEST, "SKU does not belong to merchant");
      }
      return new OrderItemSnapshot(sku.skuId(), sku.name(), sku.priceCent(), item.quantity());
    }).toList();
  }

  @Transactional
  public ReserveStockResponse reserve(ReserveStockRequest request) {
    return idempotentTemplate.execute("catalog:reserve:" + request.userId() + ":" + request.requestId(), () -> {
      List<Long> ids = new ArrayList<>();
      for (OrderSkuItem item : request.items()) {
        int affected = jdbcTemplate.update(
            "UPDATE sku SET stock = stock - ? WHERE id = ? AND merchant_id = ? AND stock >= ?",
            item.quantity(), item.skuId(), request.merchantId(), item.quantity()
        );
        if (affected != 1) {
          throw new BizException(ErrorCode.STOCK_NOT_ENOUGH, "SKU stock not enough: " + item.skuId());
        }

        long id = idGenerator.next("stockReservation");
        try {
          jdbcTemplate.update("""
              INSERT INTO stock_reservation(
                id, request_id, user_id, merchant_id, sku_id, ticket_id, order_id, quantity, status,
                expire_time, create_time, update_time
              ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
              """,
              id,
              request.requestId(),
              request.userId(),
              request.merchantId(),
              item.skuId(),
              request.ticketId(),
              request.orderId(),
              item.quantity(),
              StockReservationStatus.RESERVED.code(),
              Timestamp.valueOf(request.expireTime()),
              Timestamp.valueOf(LocalDateTime.now()),
              Timestamp.valueOf(LocalDateTime.now())
          );
          ids.add(id);
        } catch (DuplicateKeyException duplicate) {
          jdbcTemplate.update("UPDATE sku SET stock = stock + ? WHERE id = ?", item.quantity(), item.skuId());
          Long existingId = jdbcTemplate.queryForObject(
              "SELECT id FROM stock_reservation WHERE request_id = ? AND sku_id = ?",
              Long.class,
              request.requestId(),
              item.skuId()
          );
          ids.add(existingId);
        }
      }
      return new ReserveStockResponse(ids, StockReservationStatus.RESERVED.name());
    });
  }

  @Transactional
  public void confirm(List<Long> reservationIds, Long orderId) {
    for (Long id : reservationIds) {
      jdbcTemplate.update("""
          UPDATE stock_reservation
          SET status = ?, order_id = ?, update_time = ?
          WHERE id = ? AND status = ?
          """,
          StockReservationStatus.CONFIRMED.code(),
          orderId,
          Timestamp.valueOf(LocalDateTime.now()),
          id,
          StockReservationStatus.RESERVED.code()
      );
    }
  }

  @Transactional
  public void release(List<Long> reservationIds) {
    for (Long id : reservationIds) {
      StockReservationRow reservation = findReservation(id);
      int affected = jdbcTemplate.update("""
          UPDATE stock_reservation
          SET status = ?, update_time = ?
          WHERE id = ? AND status = ?
          """,
          StockReservationStatus.RELEASED.code(),
          Timestamp.valueOf(LocalDateTime.now()),
          id,
          StockReservationStatus.RESERVED.code()
      );
      if (affected == 1) {
        jdbcTemplate.update("UPDATE sku SET stock = stock + ? WHERE id = ?", reservation.quantity(), reservation.skuId());
      }
    }
  }

  public List<StockReservationView> reservations() {
    return jdbcTemplate.query("""
        SELECT id, sku_id, quantity, status, ticket_id, order_id
        FROM stock_reservation
        ORDER BY id
        """, (rs, rowNum) -> new StockReservationView(
        rs.getLong("id"),
        rs.getLong("sku_id"),
        rs.getInt("quantity"),
        statusName(rs.getInt("status")),
        nullableLong(rs, "ticket_id"),
        nullableLong(rs, "order_id")
    ));
  }

  private SkuView findSku(long skuId) {
    List<SkuView> views = jdbcTemplate.query(
        "SELECT id, merchant_id, name, price_cent, stock FROM sku WHERE id = ?",
        this::mapSku,
        skuId
    );
    if (views.isEmpty()) {
      throw new BizException(ErrorCode.NOT_FOUND, "SKU not found");
    }
    return views.get(0);
  }

  private StockReservationRow findReservation(long id) {
    List<StockReservationRow> reservations = jdbcTemplate.query(
        "SELECT id, sku_id, quantity FROM stock_reservation WHERE id = ?",
        (rs, rowNum) -> new StockReservationRow(rs.getLong("id"), rs.getLong("sku_id"), rs.getInt("quantity")),
        id
    );
    if (reservations.isEmpty()) {
      throw new BizException(ErrorCode.NOT_FOUND, "Stock reservation not found");
    }
    return reservations.get(0);
  }

  private SkuView mapSku(ResultSet rs, int rowNum) throws SQLException {
    return new SkuView(
        rs.getLong("id"),
        rs.getLong("merchant_id"),
        rs.getString("name"),
        rs.getInt("price_cent"),
        rs.getInt("stock")
    );
  }

  private Long nullableLong(ResultSet rs, String column) throws SQLException {
    long value = rs.getLong(column);
    return rs.wasNull() ? null : value;
  }

  private String statusName(int code) {
    for (StockReservationStatus status : StockReservationStatus.values()) {
      if (status.code() == code) {
        return status.name();
      }
    }
    return "UNKNOWN";
  }

  private record StockReservationRow(long id, long skuId, int quantity) {
  }
}
