package com.mealflow.catalog;

import com.mealflow.catalog.api.CategoryRequest;
import com.mealflow.catalog.api.CategoryView;
import com.mealflow.catalog.api.OrderItemSnapshot;
import com.mealflow.catalog.api.OrderSkuItem;
import com.mealflow.catalog.api.ReserveStockRequest;
import com.mealflow.catalog.api.ReserveStockResponse;
import com.mealflow.catalog.api.SkuAdminRequest;
import com.mealflow.catalog.api.SkuView;
import com.mealflow.catalog.api.StockReservationView;
import com.mealflow.catalog.mapper.CatalogMapper;
import com.mealflow.catalog.mapper.CategoryRow;
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
    ensureSkuColumns();
    idGenerator.ensureAtLeast("stockReservation", catalogMapper.maxReservationId());
    idGenerator.ensureAtLeast("sku", catalogMapper.maxSkuId());
    idGenerator.ensureAtLeast("category", catalogMapper.maxCategoryId());
  }

  public List<SkuView> listByMerchant(long merchantId) {
    return catalogMapper.findSkusByMerchant(merchantId).stream().map(this::skuView).toList();
  }

  public List<CategoryView> adminCategories(long merchantId) {
    return catalogMapper.findCategories(merchantId).stream().map(this::categoryView).toList();
  }

  public List<CategoryView> listCategories(long merchantId) {
    return catalogMapper.findActiveCategories(merchantId).stream().map(this::categoryView).toList();
  }

  public List<SkuView> adminSkus(long merchantId) {
    return catalogMapper.findAdminSkusByMerchant(merchantId).stream().map(this::skuView).toList();
  }

  public List<OrderItemSnapshot> buildSnapshots(long merchantId, List<OrderSkuItem> items) {
    return items.stream().map(item -> {
      SkuView sku = findSku(item.skuId());
      if (sku.merchantId() != merchantId) {
        throw new BizException(ErrorCode.BAD_REQUEST, "SKU does not belong to merchant");
      }
      if (!"ON_SHELF".equals(sku.status())) {
        throw new BizException(ErrorCode.BAD_REQUEST, "SKU is not on shelf");
      }
      return new OrderItemSnapshot(sku.skuId(), sku.name(), sku.priceCent(), item.quantity());
    }).toList();
  }

  @Transactional
  public CategoryView createCategory(long merchantId, CategoryRequest request) {
    long id = idGenerator.next("category");
    LocalDateTime now = LocalDateTime.now();
    catalogMapper.insertCategory(id, merchantId, request.name(), request.sortOrder(), categoryStatus(request.status()),
        now);
    return categoryView(catalogMapper.findCategory(id));
  }

  @Transactional
  public CategoryView updateCategory(long merchantId, long categoryId, CategoryRequest request) {
    requireCategory(merchantId, categoryId);
    catalogMapper.updateCategory(categoryId, merchantId, request.name(), request.sortOrder(),
        categoryStatus(request.status()), LocalDateTime.now());
    return categoryView(catalogMapper.findCategory(categoryId));
  }

  @Transactional
  public SkuView createSku(long merchantId, SkuAdminRequest request) {
    requireCategoryIfPresent(merchantId, request.categoryId());
    long id = idGenerator.next("sku");
    LocalDateTime now = LocalDateTime.now();
    catalogMapper.insertSku(id, merchantId, request.categoryId(), request.name(), text(request.description()),
        text(request.imageUrl()), request.priceCent(), request.stock(), skuStatus(request.status()), now);
    return skuView(catalogMapper.findSku(id));
  }

  @Transactional
  public SkuView updateSku(long merchantId, long skuId, SkuAdminRequest request) {
    requireSku(merchantId, skuId);
    requireCategoryIfPresent(merchantId, request.categoryId());
    int affected = catalogMapper.updateSku(skuId, merchantId, request.categoryId(), request.name(),
        text(request.description()), text(request.imageUrl()), request.priceCent(), request.stock(),
        skuStatus(request.status()), LocalDateTime.now());
    if (affected != 1) {
      throw new BizException(ErrorCode.NOT_FOUND, "SKU not found");
    }
    return skuView(catalogMapper.findSku(skuId));
  }

  @Transactional
  public SkuView updateSkuStatus(long merchantId, long skuId, String status) {
    requireSku(merchantId, skuId);
    catalogMapper.updateSkuStatus(skuId, merchantId, skuStatus(status), LocalDateTime.now());
    return skuView(catalogMapper.findSku(skuId));
  }

  @Transactional
  public SkuView updateSkuStock(long merchantId, long skuId, int stock) {
    requireSku(merchantId, skuId);
    catalogMapper.updateSkuStock(skuId, merchantId, stock, LocalDateTime.now());
    return skuView(catalogMapper.findSku(skuId));
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

  private SkuRow requireSku(long merchantId, long skuId) {
    SkuRow sku = catalogMapper.findSku(skuId);
    if (sku == null || sku.getMerchantId() != merchantId) {
      throw new BizException(ErrorCode.NOT_FOUND, "SKU not found");
    }
    return sku;
  }

  private CategoryRow requireCategory(long merchantId, long categoryId) {
    CategoryRow category = catalogMapper.findCategory(categoryId);
    if (category == null || category.getMerchantId() != merchantId) {
      throw new BizException(ErrorCode.NOT_FOUND, "category not found");
    }
    return category;
  }

  private void requireCategoryIfPresent(long merchantId, Long categoryId) {
    if (categoryId != null) {
      CategoryRow category = requireCategory(merchantId, categoryId);
      if (!"ACTIVE".equals(category.getStatus())) {
        throw new BizException(ErrorCode.BAD_REQUEST, "category is disabled");
      }
    }
  }

  private StockReservationRow findReservation(long id) {
    StockReservationRow reservation = catalogMapper.findReservation(id);
    if (reservation == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "Stock reservation not found");
    }
    return reservation;
  }

  private SkuView skuView(SkuRow sku) {
    return new SkuView(sku.getId(), sku.getMerchantId(), sku.getCategoryId(), sku.getCategoryName(), sku.getName(),
        sku.getDescription(), sku.getImageUrl(), sku.getPriceCent(), sku.getStock(), sku.getStatus());
  }

  private CategoryView categoryView(CategoryRow row) {
    return new CategoryView(row.getId(), row.getMerchantId(), row.getName(), row.getSortOrder(), row.getStatus());
  }

  private String skuStatus(String status) {
    if (status == null || status.isBlank()) {
      return "ON_SHELF";
    }
    if (!List.of("ON_SHELF", "OFF_SHELF").contains(status)) {
      throw new BizException(ErrorCode.BAD_REQUEST, "SKU status must be ON_SHELF or OFF_SHELF");
    }
    return status;
  }

  private String categoryStatus(String status) {
    if (status == null || status.isBlank()) {
      return "ACTIVE";
    }
    if (!List.of("ACTIVE", "DISABLED").contains(status)) {
      throw new BizException(ErrorCode.BAD_REQUEST, "category status must be ACTIVE or DISABLED");
    }
    return status;
  }

  private String text(String value) {
    return value == null ? "" : value;
  }

  private void ensureSkuColumns() {
    if (catalogMapper.countSkuColumn("category_id") == 0) {
      catalogMapper.addSkuCategoryIdColumn();
    }
    if (catalogMapper.countSkuColumn("description") == 0) {
      catalogMapper.addSkuDescriptionColumn();
    }
    if (catalogMapper.countSkuColumn("image_url") == 0) {
      catalogMapper.addSkuImageUrlColumn();
    }
    if (catalogMapper.countSkuColumn("status") == 0) {
      catalogMapper.addSkuStatusColumn();
    }
    if (catalogMapper.countSkuColumn("create_time") == 0) {
      catalogMapper.addSkuCreateTimeColumn();
    }
    if (catalogMapper.countSkuColumn("update_time") == 0) {
      catalogMapper.addSkuUpdateTimeColumn();
    }
    catalogMapper.hydrateSeedSkuMetadata();
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
