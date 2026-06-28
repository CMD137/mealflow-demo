package com.mealflow.catalog;

import com.mealflow.catalog.api.OrderItemSnapshot;
import com.mealflow.catalog.api.OrderSkuItem;
import com.mealflow.catalog.api.ReserveStockRequest;
import com.mealflow.catalog.api.ReserveStockResponse;
import com.mealflow.catalog.api.SkuView;
import com.mealflow.catalog.api.StockReservationView;
import com.mealflow.catalog.mapper.CatalogMapper;
import com.mealflow.catalog.mapper.SkuRow;
import com.mealflow.catalog.mapper.StockReservationRow;
import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import com.mealflow.common.status.StockReservationStatus;
import com.mealflow.infra.id.IdGenerator;
import com.mealflow.infra.idempotent.IdempotentTemplate;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
  private final CatalogMapper catalogMapper;
  private final IdGenerator idGenerator = new IdGenerator();
  private final IdempotentTemplate idempotentTemplate = new IdempotentTemplate();

  public CatalogService(CatalogMapper catalogMapper) {
    this.catalogMapper = catalogMapper;
  }

  @PostConstruct
  void initializeIdGenerator() {
    idGenerator.ensureAtLeast("stockReservation", catalogMapper.maxReservationId());
  }

  public List<SkuView> listByMerchant(long merchantId) {
    return catalogMapper.findSkusByMerchant(merchantId).stream().map(this::skuView).toList();
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
        int affected = catalogMapper.decrementStock(item.skuId(), request.merchantId(), item.quantity());
        if (affected != 1) {
          throw new BizException(ErrorCode.STOCK_NOT_ENOUGH, "SKU stock not enough: " + item.skuId());
        }

        long id = idGenerator.next("stockReservation");
        try {
          catalogMapper.insertReservation(id, request.requestId(), request.userId(), request.merchantId(),
              item.skuId(), request.ticketId(), request.orderId(), item.quantity(),
              StockReservationStatus.RESERVED.code(), request.expireTime(), LocalDateTime.now());
          ids.add(id);
        } catch (DuplicateKeyException duplicate) {
          catalogMapper.restoreStock(item.skuId(), item.quantity());
          ids.add(catalogMapper.findReservationIdByRequestSku(request.requestId(), item.skuId()));
        }
      }
      return new ReserveStockResponse(ids, StockReservationStatus.RESERVED.name());
    });
  }

  @Transactional
  public void confirm(List<Long> reservationIds, Long orderId) {
    for (Long id : reservationIds) {
      catalogMapper.confirmReservation(id, StockReservationStatus.CONFIRMED.code(), orderId,
          StockReservationStatus.RESERVED.code(), LocalDateTime.now());
    }
  }

  @Transactional
  public void release(List<Long> reservationIds) {
    for (Long id : reservationIds) {
      StockReservationRow reservation = findReservation(id);
      int affected = catalogMapper.releaseReservation(id, StockReservationStatus.RELEASED.code(),
          StockReservationStatus.RESERVED.code(), LocalDateTime.now());
      if (affected == 1) {
        catalogMapper.restoreStock(reservation.getSkuId(), reservation.getQuantity());
      }
    }
  }

  public List<StockReservationView> reservations() {
    return catalogMapper.findReservations().stream()
        .map(row -> new StockReservationView(row.getId(), row.getSkuId(), row.getQuantity(),
            statusName(row.getStatus()), row.getTicketId(), row.getOrderId()))
        .toList();
  }

  private SkuView findSku(long skuId) {
    SkuRow sku = catalogMapper.findSku(skuId);
    if (sku == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "SKU not found");
    }
    return skuView(sku);
  }

  private StockReservationRow findReservation(long id) {
    StockReservationRow reservation = catalogMapper.findReservation(id);
    if (reservation == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "Stock reservation not found");
    }
    return reservation;
  }

  private SkuView skuView(SkuRow sku) {
    return new SkuView(sku.getId(), sku.getMerchantId(), sku.getName(), sku.getPriceCent(), sku.getStock());
  }

  private String statusName(int code) {
    for (StockReservationStatus status : StockReservationStatus.values()) {
      if (status.code() == code) {
        return status.name();
      }
    }
    return "UNKNOWN";
  }
}
