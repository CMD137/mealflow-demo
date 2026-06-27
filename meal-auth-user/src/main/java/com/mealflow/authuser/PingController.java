package com.mealflow.authuser;

import com.mealflow.common.api.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingController {
  @GetMapping({"/ping", "/auth/ping", "/users/ping"})
  public Result<String> ping() {
    return Result.ok("pong from meal-auth-user");
  }
}
