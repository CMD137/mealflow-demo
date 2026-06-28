package com.mealflow.queue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MealQueueApplication {
  public static void main(String[] args) {
    SpringApplication.run(MealQueueApplication.class, args);
  }
}
