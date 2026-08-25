package com.mealflow.payment;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface PaymentBusinessSequenceMapper {
  @Select("SELECT next_value FROM business_sequence WHERE namespace = #{namespace} FOR UPDATE")
  Long lockValue(@Param("namespace") String namespace);

  @Update("UPDATE business_sequence SET next_value = #{nextValue} WHERE namespace = #{namespace}")
  int updateValue(@Param("namespace") String namespace, @Param("nextValue") long nextValue);
}
