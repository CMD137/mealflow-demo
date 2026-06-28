package com.mealflow.cart;

import com.mealflow.cart.api.AddCartItemRequest;
import com.mealflow.cart.api.CartItemView;
import com.mealflow.cart.mapper.CartItemRow;
import com.mealflow.cart.mapper.CartMapper;
import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import com.mealflow.infra.id.IdGenerator;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {
  private final IdGenerator idGenerator = new IdGenerator();
  private final CartMapper cartMapper;

  public CartService(CartMapper cartMapper) {
    this.cartMapper = cartMapper;
  }

  @PostConstruct
  void initializeIdGenerator() {
    idGenerator.ensureAtLeast("cartItem", cartMapper.maxCartItemId());
  }

  @Transactional
  public synchronized CartItemView add(long userId, AddCartItemRequest request) {
    CartItemRow existing = cartMapper.findByUserSku(userId, request.skuId());
    if (existing != null) {
      cartMapper.increaseQuantity(existing.getId(), request.quantity(), LocalDateTime.now());
      return view(cartMapper.findById(existing.getId()));
    }
    long id = idGenerator.next("cartItem");
    cartMapper.insert(id, userId, request.merchantId(), request.skuId(), request.quantity(), true,
        LocalDateTime.now());
    return view(cartMapper.findById(id));
  }

  @Transactional
  public synchronized CartItemView update(long userId, long cartItemId, int quantity) {
    CartItemRow item = requireItem(userId, cartItemId);
    cartMapper.updateQuantity(item.getId(), quantity, LocalDateTime.now());
    return view(cartMapper.findById(item.getId()));
  }

  @Transactional
  public synchronized void delete(long userId, long cartItemId) {
    CartItemRow item = requireItem(userId, cartItemId);
    cartMapper.delete(item.getId());
  }

  public List<CartItemView> list(long userId) {
    return cartMapper.findByUser(userId).stream().map(this::view).toList();
  }

  private CartItemRow requireItem(long userId, long cartItemId) {
    CartItemRow item = cartMapper.findById(cartItemId);
    if (item == null || item.getUserId() != userId) {
      throw new BizException(ErrorCode.NOT_FOUND, "cart item not found");
    }
    return item;
  }

  private CartItemView view(CartItemRow item) {
    return new CartItemView(item.getId(), item.getUserId(), item.getMerchantId(), item.getSkuId(),
        item.getQuantity(), item.isSelected());
  }
}
