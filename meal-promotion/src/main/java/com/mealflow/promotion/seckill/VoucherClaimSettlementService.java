package com.mealflow.promotion.seckill;

import com.mealflow.promotion.mapper.PromotionMapper;
import com.mealflow.promotion.mapper.UserVoucherRow;
import com.mealflow.promotion.mapper.VoucherClaimRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoucherClaimSettlementService {
  private final PromotionMapper promotionMapper;

  public VoucherClaimSettlementService(PromotionMapper promotionMapper) {
    this.promotionMapper = promotionMapper;
  }

  @Transactional
  public ClaimSettlementResult settle(SeckillClaimCommand command) {
    VoucherClaimRow claim = new VoucherClaimRow();
    claim.setEventKey(command.eventKey());
    claim.setUserId(command.userId());
    claim.setVoucherId(command.voucherId());

    if (promotionMapper.insertClaimProcessing(claim) == 0) {
      VoucherClaimRow existing = findExistingClaim(command);
      return existingResult(existing);
    }

    if (promotionMapper.decrementStock(command.voucherId()) != 1) {
      promotionMapper.markClaimSoldOut(claim.getId(), "MYSQL_STOCK_EXHAUSTED");
      return new ClaimSettlementResult("SOLD_OUT", claim.getId(), null);
    }

    UserVoucherRow userVoucher = new UserVoucherRow();
    userVoucher.setUserId(command.userId());
    userVoucher.setVoucherId(command.voucherId());
    userVoucher.setStatus("AVAILABLE");
    promotionMapper.insertUserVoucher(userVoucher);
    promotionMapper.markClaimed(claim.getId(), userVoucher.getId());
    return new ClaimSettlementResult("CLAIMED", claim.getId(), userVoucher.getId());
  }

  private ClaimSettlementResult existingResult(VoucherClaimRow existing) {
    if (existing == null) {
      throw new IllegalStateException("claim unique key exists but claim row is unavailable");
    }
    if ("CLAIMED".equals(existing.getStatus()) || "SOLD_OUT".equals(existing.getStatus())) {
      return new ClaimSettlementResult(existing.getStatus(), existing.getId(), existing.getUserVoucherId());
    }
    throw new IllegalStateException("claim is still processing: " + existing.getEventKey());
  }

  /**
   * With concurrent delivery, another transaction can win the unique key before its row is visible
   * to this transaction. A tiny bounded retry turns that normal database race into the persisted
   * result; no in-memory idempotency state is used for correctness.
   */
  private VoucherClaimRow findExistingClaim(SeckillClaimCommand command) {
    for (int attempt = 0; attempt < 5; attempt++) {
      VoucherClaimRow existing = promotionMapper.findClaimByEventKey(command.eventKey());
      if (existing == null) {
        existing = promotionMapper.findClaim(command.userId(), command.voucherId());
      }
      if (existing != null) {
        return existing;
      }
      try {
        Thread.sleep(5L);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    return null;
  }
}
