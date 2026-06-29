package com.mealflow.cart;

import com.mealflow.cart.api.AddCartItemRequest;
import com.mealflow.cart.api.CartItemView;
import com.mealflow.cart.api.SelectCartItemRequest;
import com.mealflow.cart.api.UpdateCartItemRequest;
import com.mealflow.common.api.Result;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cart")
public class CartController {
  private final CartService cartService;
  private final long defaultUserId;

  public CartController(CartService cartService, @Value("${mealflow.demo.default-user-id:100}") long defaultUserId) {
    this.cartService = cartService;
    this.defaultUserId = defaultUserId;
  }

  @PostMapping("/items")
  public Result<CartItemView> add(@RequestHeader(value = "X-User-Id", required = false) Long userId,
      @Valid @RequestBody AddCartItemRequest request) {
    return Result.ok(cartService.add(userId == null ? defaultUserId : userId, request));
  }

  @PutMapping("/items/{cartItemId}")
  public Result<CartItemView> update(@RequestHeader(value = "X-User-Id", required = false) Long userId,
      @PathVariable long cartItemId, @Valid @RequestBody UpdateCartItemRequest request) {
    return Result.ok(cartService.update(userId == null ? defaultUserId : userId, cartItemId, request.quantity()));
  }

  @PutMapping("/items/{cartItemId}/selected")
  public Result<CartItemView> select(@RequestHeader(value = "X-User-Id", required = false) Long userId,
      @PathVariable long cartItemId, @Valid @RequestBody SelectCartItemRequest request) {
    return Result.ok(cartService.select(userId == null ? defaultUserId : userId, cartItemId, request.selected()));
  }

  @DeleteMapping("/items/{cartItemId}")
  public Result<Void> delete(@RequestHeader(value = "X-User-Id", required = false) Long userId,
      @PathVariable long cartItemId) {
    cartService.delete(userId == null ? defaultUserId : userId, cartItemId);
    return Result.ok();
  }

  @DeleteMapping
  public Result<Integer> clear(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
    return Result.ok(cartService.clear(userId == null ? defaultUserId : userId));
  }

  @GetMapping
  public Result<List<CartItemView>> list(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
    return Result.ok(cartService.list(userId == null ? defaultUserId : userId));
  }
}
