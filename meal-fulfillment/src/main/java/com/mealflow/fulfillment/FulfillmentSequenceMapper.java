package com.mealflow.fulfillment;
import org.apache.ibatis.annotations.*;
@Mapper interface FulfillmentSequenceMapper {
  @Select("SELECT next_value FROM business_sequence WHERE namespace = #{name} FOR UPDATE") Long lock(@Param("name") String name);
  @Update("UPDATE business_sequence SET next_value = #{value} WHERE namespace = #{name}") int advance(@Param("name") String name, @Param("value") long value);
}
