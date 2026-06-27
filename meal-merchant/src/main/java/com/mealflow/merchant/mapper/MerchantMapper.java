package com.mealflow.merchant.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MerchantMapper {
  @Select("SELECT id, name, business_status, base_capacity, manual_factor FROM merchant ORDER BY id")
  @Results(id = "merchantMap", value = {
      @Result(column = "id", property = "id"),
      @Result(column = "name", property = "name"),
      @Result(column = "business_status", property = "businessStatus"),
      @Result(column = "base_capacity", property = "baseCapacity"),
      @Result(column = "manual_factor", property = "manualFactor")
  })
  List<MerchantRow> findAll();

  @Select("SELECT id, name, business_status, base_capacity, manual_factor FROM merchant WHERE id = #{id}")
  @ResultMap("merchantMap")
  MerchantRow findById(long id);

  @Update("""
      UPDATE merchant
      SET base_capacity = #{baseCapacity}, manual_factor = #{manualFactor}, update_time = #{now}
      WHERE id = #{id}
      """)
  int updateCapacity(@Param("id") long id, @Param("baseCapacity") int baseCapacity,
      @Param("manualFactor") double manualFactor, @Param("now") LocalDateTime now);
}
