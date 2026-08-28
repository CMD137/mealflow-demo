package com.mealflow.cart;

import com.mealflow.cart.api.AddCartItemRequest;
import com.mealflow.cart.api.CartItemView;
import com.mealflow.cart.mapper.CartItemRow;
import com.mealflow.cart.mapper.CartMapper;
import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {
  private final CartDatabaseIdGenerator idGenerator;
  private final CartMapper cartMapper;

  public CartService(CartMapper cartMapper, CartDatabaseIdGenerator idGenerator) {
    this.cartMapper = cartMapper;
    this.idGenerator = idGenerator;
  }

  @Transactional
  public synchronized CartItemView add(long userId, AddCartItemRequest request) {
    CartItemRow existing = cartMapper.findByUserSku(userId, request.skuId());
    if (existing != null) {
      cartMapper.increaseQuantity(existing.getId(), request.quantity(), LocalDateTime.now());
      return view(cartMapper.findById(existing.getId()));
    }
    Long activeMerchantId = cartMapper.findActiveMerchantId(userId);
    if (activeMerchantId != null && activeMerchantId.longValue() != request.merchantId()) {
      throw new BizException(ErrorCode.CART_MERCHANT_CONFLICT,
          "购物车已有其他商户商品，请先清空购物车后再添加");
    }
    long id = idGenerator.next();
    try {
      cartMapper.insert(id, userId, request.merchantId(), request.skuId(), request.quantity(), true,
          LocalDateTime.now());
    } catch (DuplicateKeyException duplicate) {
      CartItemRow concurrent = cartMapper.findByUserSku(userId, request.skuId());
      if (concurrent == null) throw duplicate;
      cartMapper.increaseQuantity(concurrent.getId(), request.quantity(), LocalDateTime.now());
      return view(cartMapper.findById(concurrent.getId()));
    }
    return view(cartMapper.findById(id));
  }

  @Transactional
  public synchronized CartItemView update(long userId, long cartItemId, int quantity) {
    CartItemRow item = requireItem(userId, cartItemId);
    cartMapper.updateQuantity(item.getId(), quantity, LocalDateTime.now());
    return view(cartMapper.findById(item.getId()));
  }

  @Transactional
  public synchronized CartItemView select(long userId, long cartItemId, boolean selected) {
    CartItemRow item = requireItem(userId, cartItemId);
    cartMapper.updateSelected(item.getId(), selected, LocalDateTime.now());
    return view(cartMapper.findById(item.getId()));
  }

  @Transactional
  public synchronized void delete(long userId, long cartItemId) {
    CartItemRow item = requireItem(userId, cartItemId);
    cartMapper.delete(item.getId());
  }

  @Transactional
  public synchronized int clear(long userId) {
    return cartMapper.deleteByUser(userId);
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
