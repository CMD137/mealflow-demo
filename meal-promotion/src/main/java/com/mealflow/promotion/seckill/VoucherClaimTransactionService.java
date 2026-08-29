package com.mealflow.promotion.seckill;

import com.mealflow.promotion.mapper.PromotionMapper;
import com.mealflow.promotion.mapper.UserVoucherRow;
import com.mealflow.promotion.mapper.VoucherClaimRow;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoucherClaimTransactionService {
  private final PromotionMapper promotionMapper;

  public VoucherClaimTransactionService(PromotionMapper promotionMapper) {
    this.promotionMapper = promotionMapper;
  }

  @Transactional
  public ClaimSettlementResult settleNew(SeckillClaimCommand command) {
    VoucherClaimRow claim = new VoucherClaimRow();
    claim.setEventKey(command.eventKey());
    claim.setUserId(command.userId());
    claim.setVoucherId(command.voucherId());
    try {
      promotionMapper.insertClaimProcessing(claim);
    } catch (DuplicateKeyException ex) {
      // Only the claim insert represents an idempotent duplicate message. A later
      // unique-key failure (for example user_voucher) is a real consistency error.
      throw new DuplicateClaimException(ex);
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
}
