package com.mealflow.promotion.seckill;

import com.mealflow.promotion.mapper.PromotionMapper;
import com.mealflow.promotion.mapper.VoucherClaimRow;
import org.springframework.dao.DuplicateKeyException;
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
    } catch (DuplicateKeyException ex) {
      return existingResult(findExistingClaim(command), command, ex);
    }
  }

  private ClaimSettlementResult existingResult(VoucherClaimRow existing, SeckillClaimCommand command,
      DuplicateKeyException cause) {
    if (existing == null) {
      throw new IllegalStateException("duplicate claim conflict but persisted claim is unavailable: eventKey="
          + command.eventKey() + ", userId=" + command.userId() + ", voucherId=" + command.voucherId(), cause);
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
