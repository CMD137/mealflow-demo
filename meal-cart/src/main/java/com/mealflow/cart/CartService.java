package com.mealflow.cart;

import com.mealflow.cart.api.AddCartItemRequest;
import com.mealflow.cart.api.CartItemView;
import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import com.mealflow.infra.id.IdGenerator;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class CartService {
  private final IdGenerator idGenerator = new IdGenerator();
  private final Map<Long, CartItem> items = new ConcurrentHashMap<>();

  public synchronized CartItemView add(long userId, AddCartItemRequest request) {
    CartItem existing = items.values().stream()
        .filter(item -> item.userId == userId && item.skuId == request.skuId())
        .findFirst()
        .orElse(null);
    if (existing != null) {
      existing.quantity += request.quantity();
      return view(existing);
    }
    long id = idGenerator.next("cartItem");
    CartItem item = new CartItem(id, userId, request.merchantId(), request.skuId(), request.quantity(), true);
    items.put(id, item);
    return view(item);
  }

  public synchronized CartItemView update(long userId, long cartItemId, int quantity) {
    CartItem item = requireItem(userId, cartItemId);
    item.quantity = quantity;
    return view(item);
  }

  public synchronized void delete(long userId, long cartItemId) {
    requireItem(userId, cartItemId);
    items.remove(cartItemId);
  }

  public List<CartItemView> list(long userId) {
    return items.values().stream()
        .filter(item -> item.userId == userId && item.quantity > 0)
        .sorted(Comparator.comparingLong(item -> item.id))
        .map(this::view)
        .toList();
  }

  private CartItem requireItem(long userId, long cartItemId) {
    CartItem item = items.get(cartItemId);
    if (item == null || item.userId != userId) {
      throw new BizException(ErrorCode.NOT_FOUND, "购物车项不存在");
    }
    return item;
  }

  private CartItemView view(CartItem item) {
    return new CartItemView(item.id, item.userId, item.merchantId, item.skuId, item.quantity, item.selected);
  }

  static class CartItem {
    final long id;
    final long userId;
    final long merchantId;
    final long skuId;
    int quantity;
    boolean selected;

    CartItem(long id, long userId, long merchantId, long skuId, int quantity, boolean selected) {
      this.id = id;
      this.userId = userId;
      this.merchantId = merchantId;
      this.skuId = skuId;
      this.quantity = quantity;
      this.selected = selected;
    }
  }
}
