package com.mealflow.promotion.seckill;

import com.mealflow.promotion.mapper.PromotionMapper;
import com.mealflow.promotion.mapper.VoucherClaimRow;
import org.springframework.stereotype.Service;

@Service
public class VoucherClaimSettlementService {
  private final PromotionMapper promotionMapper;
  private final VoucherClaimTransactionService transactionService;

  public VoucherClaimSettlementService(PromotionMapper promotionMapper,
      VoucherClaimTransactionService transactionService) {
    this.promotionMapper = promotionMapper;
    this.transactionService = transactionService;
  }

  public ClaimSettlementResult settle(SeckillClaimCommand command) {
    try {
      return transactionService.settleNew(command);
    } catch (DuplicateClaimException ex) {
      return existingResult(findExistingClaim(command), command, ex);
    }
  }

  private ClaimSettlementResult existingResult(VoucherClaimRow existing, SeckillClaimCommand command,
      DuplicateClaimException cause) {
    if (existing == null) {
      throw cause.duplicateCause();
    }
    if ("CLAIMED".equals(existing.getStatus()) || "SOLD_OUT".equals(existing.getStatus())) {
      return new ClaimSettlementResult(existing.getStatus(), existing.getId(), existing.getUserVoucherId());
    }
    throw new IllegalStateException("claim is still processing: eventKey=" + existing.getEventKey()
        + ", userId=" + existing.getUserId() + ", voucherId=" + existing.getVoucherId(), cause);
  }

  private VoucherClaimRow findExistingClaim(SeckillClaimCommand command) {
    VoucherClaimRow existing = promotionMapper.findClaimByEventKey(command.eventKey());
    if (existing == null) {
      existing = promotionMapper.findClaim(command.userId(), command.voucherId());
    }
    return existing;
  }
}
