package com.mealflow.fulfillment;
import org.springframework.stereotype.Component;
@Component public class FulfillmentIdGenerator {
  private final FulfillmentSequenceMapper mapper;
  public FulfillmentIdGenerator(FulfillmentSequenceMapper mapper) { this.mapper = mapper; }
  public long next(String name) { Long value = mapper.lock(name); if (value == null || mapper.advance(name, value + 1) != 1) throw new IllegalStateException("fulfillment sequence unavailable: " + name); return value + 1; }
}
