package com.mealflow.authuser;

import com.mealflow.authuser.api.AddressView;
import com.mealflow.authuser.api.AddressRequest;
import com.mealflow.authuser.api.LoginRequest;
import com.mealflow.authuser.api.LoginResponse;
import com.mealflow.authuser.api.TokenPrincipalView;
import com.mealflow.authuser.api.TokenValidationRequest;
import com.mealflow.authuser.api.UserView;
import com.mealflow.common.api.Result;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthUserController {
  private final AuthUserService authUserService;
  private final long defaultUserId;

  public AuthUserController(AuthUserService authUserService,
      @Value("${mealflow.demo.default-user-id:100}") long defaultUserId) {
    this.authUserService = authUserService;
    this.defaultUserId = defaultUserId;
  }

  @PostMapping("/auth/login")
  public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    return Result.ok(authUserService.login(request));
  }

  @PostMapping("/auth/internal/tokens/validate")
  public Result<TokenPrincipalView> validate(@Valid @RequestBody TokenValidationRequest request) {
    TokenPrincipalView principal = authUserService.validateToken(request.token());
    if (principal == null) {
      return new Result<>(false, "UNAUTHORIZED", "invalid token", null);
    }
    return Result.ok(principal);
  }

  @GetMapping("/users/me")
  public Result<UserView> me(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
    return Result.ok(authUserService.get(userId == null ? defaultUserId : userId));
  }

  @GetMapping("/users/addresses")
  public Result<List<AddressView>> addresses(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
    return Result.ok(authUserService.addresses(userId == null ? defaultUserId : userId));
  }

  @PostMapping("/users/addresses")
  public Result<AddressView> addAddress(@RequestHeader(value = "X-User-Id", required = false) Long userId,
      @Valid @RequestBody AddressRequest request) {
    return Result.ok(authUserService.addAddress(userId == null ? defaultUserId : userId, request));
  }

  @PutMapping("/users/addresses/{addressId}")
  public Result<AddressView> updateAddress(@RequestHeader(value = "X-User-Id", required = false) Long userId,
      @PathVariable long addressId, @Valid @RequestBody AddressRequest request) {
    return Result.ok(authUserService.updateAddress(userId == null ? defaultUserId : userId, addressId, request));
  }

  @DeleteMapping("/users/addresses/{addressId}")
  public Result<Void> deleteAddress(@RequestHeader(value = "X-User-Id", required = false) Long userId,
      @PathVariable long addressId) {
    authUserService.deleteAddress(userId == null ? defaultUserId : userId, addressId);
    return Result.ok();
  }
}
