package com.mealflow.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MealPaymentApplication {
  public static void main(String[] args) {
    SpringApplication.run(MealPaymentApplication.class, args);
  }
}
