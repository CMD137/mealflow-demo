package com.mealflow.cart;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface CartSequenceMapper {
  @Select("SELECT next_value FROM business_sequence WHERE namespace = 'cartItem' FOR UPDATE") Long lockValue();
  @Update("UPDATE business_sequence SET next_value = #{nextValue} WHERE namespace = 'cartItem'") int updateValue(long nextValue);
}
