package com.mealflow.merchant;

import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.api.PageResult;
import com.mealflow.common.exception.BizException;
import com.mealflow.merchant.api.CapacityConfigRequest;
import com.mealflow.merchant.api.BusinessStatusRequest;
import com.mealflow.merchant.api.MerchantView;
import com.mealflow.merchant.api.SystemMerchantStatusRequest;
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

  public PageResult<MerchantView> systemMerchants(int page, int pageSize, String name, String businessStatus) {
    int normalizedPage = Math.max(page, 1);
    int normalizedPageSize = Math.min(Math.max(pageSize, 1), 100);
    String normalizedStatus = businessStatus == null ? null : businessStatus.trim().toUpperCase();
    if (normalizedStatus != null && !normalizedStatus.isBlank()
        && !List.of("OPEN", "CLOSED", "SUSPENDED").contains(normalizedStatus)) {
      throw new BizException(ErrorCode.BAD_REQUEST, "unsupported merchant business status");
    }
    long total = merchantMapper.countSystemPage(name, normalizedStatus);
    List<MerchantView> items = merchantMapper.findSystemPage(name, normalizedStatus, normalizedPageSize,
        (normalizedPage - 1) * normalizedPageSize).stream().map(this::view).toList();
    return PageResult.of(items, total, normalizedPage, normalizedPageSize);
  }

  @Transactional
  public MerchantView updateCapacity(long merchantId, CapacityConfigRequest request) {
    requireMerchant(merchantId);
    double manualFactor = request.manualFactor() <= 0 ? 1.0 : request.manualFactor();
    merchantMapper.updateCapacity(merchantId, request.baseCapacity(), manualFactor, LocalDateTime.now());
    return get(merchantId);
  }

  @Transactional
  public MerchantView updateBusinessStatus(long merchantId, BusinessStatusRequest request) {
    requireMerchant(merchantId);
    String status = request.businessStatus().trim().toUpperCase();
    if (!List.of("OPEN", "CLOSED").contains(status)) {
      throw new BizException(ErrorCode.BAD_REQUEST, "unsupported merchant business status");
    }
    if ("SUSPENDED".equals(requireMerchant(merchantId).getBusinessStatus())) {
      throw new BizException(ErrorCode.FORBIDDEN, "suspended merchant must be restored by system administration");
    }
    merchantMapper.updateBusinessStatus(merchantId, status, LocalDateTime.now());
    return get(merchantId);
  }

  @Transactional
  public MerchantView updateSystemBusinessStatus(long merchantId, SystemMerchantStatusRequest request) {
    requireMerchant(merchantId);
    String status = request.businessStatus().trim().toUpperCase();
    if (!List.of("OPEN", "CLOSED", "SUSPENDED").contains(status)) {
      throw new BizException(ErrorCode.BAD_REQUEST, "unsupported merchant business status");
    }
    merchantMapper.updateBusinessStatus(merchantId, status, LocalDateTime.now());
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
