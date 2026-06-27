package com.mealflow.merchant;

import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import com.mealflow.merchant.api.CapacityConfigRequest;
import com.mealflow.merchant.api.MerchantView;
import jakarta.annotation.PostConstruct;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class MerchantService {
  private final Map<Long, Merchant> merchants = new ConcurrentHashMap<>();

  @PostConstruct
  void seed() {
    merchants.put(10L, new Merchant(10L, "MealFlow 牛肉饭", "OPEN", 1, 1.0));
    merchants.put(11L, new Merchant(11L, "MealFlow 轻食", "OPEN", 3, 1.0));
  }

  public List<MerchantView> list() {
    return merchants.values().stream()
        .sorted(Comparator.comparingLong(merchant -> merchant.id))
        .map(this::view)
        .toList();
  }

  public MerchantView get(long merchantId) {
    return view(requireMerchant(merchantId));
  }

  public MerchantView updateCapacity(long merchantId, CapacityConfigRequest request) {
    Merchant merchant = requireMerchant(merchantId);
    merchant.baseCapacity = request.baseCapacity();
    merchant.manualFactor = request.manualFactor() <= 0 ? 1.0 : request.manualFactor();
    return view(merchant);
  }

  private Merchant requireMerchant(long merchantId) {
    Merchant merchant = merchants.get(merchantId);
    if (merchant == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "商户不存在");
    }
    return merchant;
  }

  private MerchantView view(Merchant merchant) {
    return new MerchantView(merchant.id, merchant.name, merchant.businessStatus, merchant.baseCapacity,
        merchant.manualFactor);
  }

  static class Merchant {
    final long id;
    final String name;
    final String businessStatus;
    int baseCapacity;
    double manualFactor;

    Merchant(long id, String name, String businessStatus, int baseCapacity, double manualFactor) {
      this.id = id;
      this.name = name;
      this.businessStatus = businessStatus;
      this.baseCapacity = baseCapacity;
      this.manualFactor = manualFactor;
    }
  }
}
