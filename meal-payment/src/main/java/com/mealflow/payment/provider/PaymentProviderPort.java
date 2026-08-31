package com.mealflow.payment.provider;

import java.util.Map;
import java.time.LocalDateTime;

public interface PaymentProviderPort {
  String code();
  String checkoutUrl(long payOrderId, int amountCent, LocalDateTime expireAt);
  CloseResult close(String merchantOrderNo);
  boolean verifyCallback(Map<String, String> parameters);

  RefundResult refund(String merchantOrderNo, String refundRequestNo, int amountCent);

  RefundResult queryRefund(String merchantOrderNo, String refundRequestNo);

  record RefundResult(boolean successful, boolean processing, String channelTransactionNo,
                      String channelRefundNo, String message, String rawResponse) {
  }

  record CloseResult(boolean closed, boolean alreadyPaid, String message, String rawResponse) {
  }
}
