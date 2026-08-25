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
      VoucherClaimRow existing = promotionMapper.findClaimByEventKey(command.eventKey());
      if (existing == null) {
        existing = promotionMapper.findClaim(command.userId(), command.voucherId());
      }
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
}
