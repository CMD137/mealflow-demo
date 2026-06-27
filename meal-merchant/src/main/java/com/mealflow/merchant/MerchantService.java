package com.mealflow.merchant;

import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import com.mealflow.merchant.api.CapacityConfigRequest;
import com.mealflow.merchant.api.MerchantView;
import com.mealflow.merchant.mapper.MerchantMapper;
import com.mealflow.merchant.mapper.MerchantRow;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MerchantService {
  private final MerchantMapper merchantMapper;

  public MerchantService(MerchantMapper merchantMapper) {
    this.merchantMapper = merchantMapper;
  }

  public List<MerchantView> list() {
    return merchantMapper.findAll().stream().map(this::view).toList();
  }

  public MerchantView get(long merchantId) {
    return view(requireMerchant(merchantId));
  }

  @Transactional
  public MerchantView updateCapacity(long merchantId, CapacityConfigRequest request) {
    requireMerchant(merchantId);
    double manualFactor = request.manualFactor() <= 0 ? 1.0 : request.manualFactor();
    merchantMapper.updateCapacity(merchantId, request.baseCapacity(), manualFactor, LocalDateTime.now());
    return get(merchantId);
  }

  private MerchantRow requireMerchant(long merchantId) {
    MerchantRow merchant = merchantMapper.findById(merchantId);
    if (merchant == null) {
      throw new BizException(ErrorCode.NOT_FOUND, "merchant not found");
    }
    return merchant;
  }

  private MerchantView view(MerchantRow merchant) {
    return new MerchantView(merchant.getId(), merchant.getName(), merchant.getBusinessStatus(),
        merchant.getBaseCapacity(), merchant.getManualFactor());
  }
}
