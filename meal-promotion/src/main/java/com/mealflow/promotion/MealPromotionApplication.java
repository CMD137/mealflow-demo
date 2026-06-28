package com.mealflow.promotion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MealPromotionApplication {
  public static void main(String[] args) {
    SpringApplication.run(MealPromotionApplication.class, args);
  }
}
