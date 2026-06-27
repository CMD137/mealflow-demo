package com.mealflow.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MealOrderApplication {
  public static void main(String[] args) {
    SpringApplication.run(MealOrderApplication.class, args);
  }
}
