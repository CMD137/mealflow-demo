package com.mealflow.cart;

import static org.assertj.core.api.Assertions.assertThat;

import com.mealflow.cart.api.AddCartItemRequest;
import com.mealflow.cart.api.CartItemView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = "spring.cloud.nacos.discovery.enabled=false"
)
class CartPersistenceTest {
  @Autowired
  private CartService cartService;

  @Test
  void addsUpdatesAndDeletesCartItemInDatabase() {
    CartItemView added = cartService.add(101L, new AddCartItemRequest(10L, 1L, 2));
    CartItemView merged = cartService.add(101L, new AddCartItemRequest(10L, 1L, 3));

    assertThat(merged.cartItemId()).isEqualTo(added.cartItemId());
    assertThat(merged.quantity()).isEqualTo(5);

    CartItemView updated = cartService.update(101L, added.cartItemId(), 1);
    assertThat(updated.quantity()).isEqualTo(1);

    CartItemView unselected = cartService.select(101L, added.cartItemId(), false);
    assertThat(unselected.selected()).isFalse();

    cartService.delete(101L, added.cartItemId());
    assertThat(cartService.list(101L)).isEmpty();
  }

  @Test
  void clearsCartItemsInDatabase() {
    cartService.add(102L, new AddCartItemRequest(10L, 1L, 2));
    cartService.add(102L, new AddCartItemRequest(10L, 2L, 1));

    int cleared = cartService.clear(102L);

    assertThat(cleared).isEqualTo(2);
    assertThat(cartService.list(102L)).isEmpty();
  }
}
