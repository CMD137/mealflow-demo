package com.mealflow.catalog.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CatalogMapper {
  @Select("SELECT COALESCE(MAX(id), 10000) FROM stock_reservation")
  long maxReservationId();

  @Select("SELECT COALESCE(MAX(id), 10000) FROM sku")
  long maxSkuId();

  @Select("SELECT COALESCE(MAX(id), 10000) FROM category")
  long maxCategoryId();

  @Select("""
      SELECT COUNT(*)
      FROM INFORMATION_SCHEMA.COLUMNS
      WHERE UPPER(TABLE_NAME) = UPPER('sku') AND UPPER(COLUMN_NAME) = UPPER(#{columnName})
      """)
  int countSkuColumn(String columnName);

  @Update("ALTER TABLE sku ADD COLUMN category_id BIGINT NULL")
  int addSkuCategoryIdColumn();

  @Update("ALTER TABLE sku ADD COLUMN description VARCHAR(255) NOT NULL DEFAULT ''")
  int addSkuDescriptionColumn();

  @Update("ALTER TABLE sku ADD COLUMN image_url VARCHAR(255) NOT NULL DEFAULT ''")
  int addSkuImageUrlColumn();

  @Update("ALTER TABLE sku ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'ON_SHELF'")
  int addSkuStatusColumn();

  @Update("ALTER TABLE sku ADD COLUMN create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP")
  int addSkuCreateTimeColumn();

  @Update("ALTER TABLE sku ADD COLUMN update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP")
  int addSkuUpdateTimeColumn();

  @Update("""
      UPDATE sku
      SET category_id = CASE
            WHEN category_id IS NULL AND id IN (1, 2) THEN 1
            WHEN category_id IS NULL AND id = 3 THEN 2
            ELSE category_id
          END,
          description = CASE
            WHEN description = '' AND id = 1 THEN 'Beef rice bowl for lunch peak'
            WHEN description = '' AND id = 2 THEN 'Grilled chicken rice bowl'
            WHEN description = '' AND id = 3 THEN 'Cold lemon tea'
            ELSE description
          END,
          update_time = CURRENT_TIMESTAMP
      WHERE merchant_id = 10 AND id IN (1, 2, 3)
      """)
  int hydrateSeedSkuMetadata();

  @Select("""
      SELECT s.id, s.merchant_id, s.category_id, c.name AS category_name, s.name, s.description,
             s.image_url, s.price_cent, s.stock, s.status
      FROM sku s
      LEFT JOIN category c ON c.id = s.category_id
      WHERE s.merchant_id = #{merchantId} AND s.status = 'ON_SHELF'
      ORDER BY COALESCE(c.sort_order, 999999), s.id
      """)
  @Results(id = "skuMap", value = {
      @Result(column = "id", property = "id"),
      @Result(column = "merchant_id", property = "merchantId"),
      @Result(column = "category_id", property = "categoryId"),
      @Result(column = "category_name", property = "categoryName"),
      @Result(column = "name", property = "name"),
      @Result(column = "description", property = "description"),
      @Result(column = "image_url", property = "imageUrl"),
      @Result(column = "price_cent", property = "priceCent"),
      @Result(column = "stock", property = "stock"),
      @Result(column = "status", property = "status")
  })
  List<SkuRow> findSkusByMerchant(long merchantId);

  @Select("""
      SELECT s.id, s.merchant_id, s.category_id, c.name AS category_name, s.name, s.description,
             s.image_url, s.price_cent, s.stock, s.status
      FROM sku s
      LEFT JOIN category c ON c.id = s.category_id
      WHERE s.merchant_id = #{merchantId}
      ORDER BY COALESCE(c.sort_order, 999999), s.id
      """)
  @ResultMap("skuMap")
  List<SkuRow> findAdminSkusByMerchant(long merchantId);

  @Select("""
      SELECT s.id, s.merchant_id, s.category_id, c.name AS category_name, s.name, s.description,
             s.image_url, s.price_cent, s.stock, s.status
      FROM sku s
      LEFT JOIN category c ON c.id = s.category_id
      WHERE s.id = #{id}
      """)
  @ResultMap("skuMap")
  SkuRow findSku(long id);

  @Insert("""
      INSERT INTO sku(
        id, merchant_id, category_id, name, description, image_url, price_cent, stock, status, create_time, update_time
      ) VALUES (
        #{id}, #{merchantId}, #{categoryId}, #{name}, #{description}, #{imageUrl}, #{priceCent}, #{stock},
        #{status}, #{now}, #{now}
      )
      """)
  int insertSku(@Param("id") long id, @Param("merchantId") long merchantId, @Param("categoryId") Long categoryId,
      @Param("name") String name, @Param("description") String description, @Param("imageUrl") String imageUrl,
      @Param("priceCent") int priceCent, @Param("stock") int stock, @Param("status") String status,
      @Param("now") LocalDateTime now);

  @Update("""
      UPDATE sku
      SET category_id = #{categoryId}, name = #{name}, description = #{description}, image_url = #{imageUrl},
          price_cent = #{priceCent}, stock = #{stock}, status = #{status}, update_time = #{now}
      WHERE id = #{id} AND merchant_id = #{merchantId}
      """)
  int updateSku(@Param("id") long id, @Param("merchantId") long merchantId, @Param("categoryId") Long categoryId,
      @Param("name") String name, @Param("description") String description, @Param("imageUrl") String imageUrl,
      @Param("priceCent") int priceCent, @Param("stock") int stock, @Param("status") String status,
      @Param("now") LocalDateTime now);

  @Update("""
      UPDATE sku
      SET status = #{status}, update_time = #{now}
      WHERE id = #{id} AND merchant_id = #{merchantId}
      """)
  int updateSkuStatus(@Param("id") long id, @Param("merchantId") long merchantId, @Param("status") String status,
      @Param("now") LocalDateTime now);

  @Update("""
      UPDATE sku
      SET stock = #{stock}, update_time = #{now}
      WHERE id = #{id} AND merchant_id = #{merchantId}
      """)
  int updateSkuStock(@Param("id") long id, @Param("merchantId") long merchantId, @Param("stock") int stock,
      @Param("now") LocalDateTime now);

  @Select("""
      SELECT id, merchant_id, name, sort_order, status
      FROM category
      WHERE merchant_id = #{merchantId}
      ORDER BY sort_order, id
      """)
  @Results(id = "categoryMap", value = {
      @Result(column = "id", property = "id"),
      @Result(column = "merchant_id", property = "merchantId"),
      @Result(column = "name", property = "name"),
      @Result(column = "sort_order", property = "sortOrder"),
      @Result(column = "status", property = "status")
  })
  List<CategoryRow> findCategories(long merchantId);

  @Select("""
      SELECT id, merchant_id, name, sort_order, status
      FROM category
      WHERE merchant_id = #{merchantId} AND status = 'ACTIVE'
      ORDER BY sort_order, id
      """)
  @ResultMap("categoryMap")
  List<CategoryRow> findActiveCategories(long merchantId);

  @Select("SELECT id, merchant_id, name, sort_order, status FROM category WHERE id = #{id}")
  @ResultMap("categoryMap")
  CategoryRow findCategory(long id);

  @Insert("""
      INSERT INTO category (id, merchant_id, name, sort_order, status, create_time, update_time)
      VALUES (#{id}, #{merchantId}, #{name}, #{sortOrder}, #{status}, #{now}, #{now})
      """)
  int insertCategory(@Param("id") long id, @Param("merchantId") long merchantId, @Param("name") String name,
      @Param("sortOrder") int sortOrder, @Param("status") String status, @Param("now") LocalDateTime now);

  @Update("""
      UPDATE category
      SET name = #{name}, sort_order = #{sortOrder}, status = #{status}, update_time = #{now}
      WHERE id = #{id} AND merchant_id = #{merchantId}
      """)
  int updateCategory(@Param("id") long id, @Param("merchantId") long merchantId, @Param("name") String name,
      @Param("sortOrder") int sortOrder, @Param("status") String status, @Param("now") LocalDateTime now);

  @Update("""
      UPDATE sku
      SET stock = stock - #{quantity}
      WHERE id = #{skuId} AND merchant_id = #{merchantId} AND stock >= #{quantity}
      """)
  int decrementStock(@Param("skuId") long skuId, @Param("merchantId") long merchantId,
      @Param("quantity") int quantity);

  @Update("UPDATE sku SET stock = stock + #{quantity} WHERE id = #{skuId}")
  int restoreStock(@Param("skuId") long skuId, @Param("quantity") int quantity);

  @Insert("""
      INSERT INTO stock_reservation(
        id, request_id, user_id, merchant_id, sku_id, ticket_id, order_id, quantity, status,
        expire_time, create_time, update_time
      ) VALUES (
        #{id}, #{requestId}, #{userId}, #{merchantId}, #{skuId}, #{ticketId}, #{orderId}, #{quantity},
        #{status}, #{expireTime}, #{now}, #{now}
      )
      """)
  int insertReservation(@Param("id") long id, @Param("requestId") String requestId, @Param("userId") long userId,
      @Param("merchantId") long merchantId, @Param("skuId") long skuId, @Param("ticketId") Long ticketId,
      @Param("orderId") Long orderId, @Param("quantity") int quantity, @Param("status") int status,
      @Param("expireTime") LocalDateTime expireTime, @Param("now") LocalDateTime now);

  @Select("SELECT id FROM stock_reservation WHERE request_id = #{requestId} AND sku_id = #{skuId}")
  Long findReservationIdByRequestSku(@Param("requestId") String requestId, @Param("skuId") long skuId);

  @Update("""
      UPDATE stock_reservation
      SET status = #{status}, order_id = #{orderId}, update_time = #{now}
      WHERE id = #{id} AND status = #{expectedStatus}
      """)
  int confirmReservation(@Param("id") long id, @Param("status") int status, @Param("orderId") Long orderId,
      @Param("expectedStatus") int expectedStatus, @Param("now") LocalDateTime now);

  @Update("""
      UPDATE stock_reservation
      SET status = #{status}, update_time = #{now}
      WHERE id = #{id} AND status = #{expectedStatus}
      """)
  int releaseReservation(@Param("id") long id, @Param("status") int status,
      @Param("expectedStatus") int expectedStatus, @Param("now") LocalDateTime now);

  @Select("SELECT id, sku_id, quantity, status, ticket_id, order_id FROM stock_reservation WHERE id = #{id}")
  @Results(id = "reservationMap", value = {
      @Result(column = "id", property = "id"),
      @Result(column = "sku_id", property = "skuId"),
      @Result(column = "quantity", property = "quantity"),
      @Result(column = "status", property = "status"),
      @Result(column = "ticket_id", property = "ticketId"),
      @Result(column = "order_id", property = "orderId")
  })
  StockReservationRow findReservation(long id);

  @Select("SELECT id, sku_id, quantity, status, ticket_id, order_id FROM stock_reservation ORDER BY id")
  @ResultMap("reservationMap")
  List<StockReservationRow> findReservations();
}
