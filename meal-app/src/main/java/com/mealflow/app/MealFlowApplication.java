package com.mealflow.app;

import com.mealflow.infra.consumer.ConsumerRecordTemplate;
import com.mealflow.infra.event.LocalEventStore;
import com.mealflow.infra.id.IdGenerator;
import com.mealflow.infra.idempotent.IdempotentTemplate;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class MealFlowApplication {
  public static void main(String[] args) {
    SpringApplication.run(MealFlowApplication.class, args);
  }

  @Bean
  IdGenerator idGenerator() {
    return new IdGenerator();
  }

  @Bean
  IdempotentTemplate idempotentTemplate() {
    return new IdempotentTemplate();
  }

  @Bean
  LocalEventStore localEventStore() {
    return new LocalEventStore();
  }

  @Bean
  ConsumerRecordTemplate consumerRecordTemplate() {
    return new ConsumerRecordTemplate();
  }
}
