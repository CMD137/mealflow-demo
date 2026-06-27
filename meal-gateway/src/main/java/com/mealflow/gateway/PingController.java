package com.mealflow.gateway;

import com.mealflow.common.api.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class PingController {
  @GetMapping("/ping")
  public Mono<Result<String>> ping() {
    return Mono.just(Result.ok("pong from meal-gateway"));
  }
}
