package com.mealflow.cart.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CartMapper {
  @Select("SELECT id, user_id, merchant_id, sku_id, quantity, selected FROM cart_item WHERE user_id = #{userId} AND sku_id = #{skuId}")
  @Results(id = "cartItemMap", value = {
      @Result(column = "id", property = "id"),
      @Result(column = "user_id", property = "userId"),
      @Result(column = "merchant_id", property = "merchantId"),
      @Result(column = "sku_id", property = "skuId"),
      @Result(column = "quantity", property = "quantity"),
      @Result(column = "selected", property = "selected")
  })
  CartItemRow findByUserSku(@Param("userId") long userId, @Param("skuId") long skuId);

  @Select("SELECT id, user_id, merchant_id, sku_id, quantity, selected FROM cart_item WHERE id = #{id}")
  @ResultMap("cartItemMap")
  CartItemRow findById(long id);

  @Select("SELECT id, user_id, merchant_id, sku_id, quantity, selected FROM cart_item WHERE user_id = #{userId} AND quantity > 0 ORDER BY id")
  @ResultMap("cartItemMap")
  List<CartItemRow> findByUser(long userId);

  @Insert("""
      INSERT INTO cart_item (id, user_id, merchant_id, sku_id, quantity, selected, create_time, update_time)
      VALUES (#{id}, #{userId}, #{merchantId}, #{skuId}, #{quantity}, #{selected}, #{now}, #{now})
      """)
  int insert(@Param("id") long id, @Param("userId") long userId, @Param("merchantId") long merchantId,
      @Param("skuId") long skuId, @Param("quantity") int quantity, @Param("selected") boolean selected,
      @Param("now") LocalDateTime now);

  @Update("""
      UPDATE cart_item
      SET quantity = quantity + #{delta}, update_time = #{now}
      WHERE id = #{id}
      """)
  int increaseQuantity(@Param("id") long id, @Param("delta") int delta, @Param("now") LocalDateTime now);

  @Update("""
      UPDATE cart_item
      SET quantity = #{quantity}, update_time = #{now}
      WHERE id = #{id}
      """)
  int updateQuantity(@Param("id") long id, @Param("quantity") int quantity, @Param("now") LocalDateTime now);

  @Delete("DELETE FROM cart_item WHERE id = #{id}")
  int delete(long id);
}
