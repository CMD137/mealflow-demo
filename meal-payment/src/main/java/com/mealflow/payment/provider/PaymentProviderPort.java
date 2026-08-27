package com.mealflow.payment.provider;

import java.util.Map;

public interface PaymentProviderPort {
  String code();
  String checkoutUrl(long payOrderId, int amountCent);
  boolean verifyCallback(Map<String, String> parameters);

  RefundResult refund(String merchantOrderNo, String refundRequestNo, int amountCent);

  RefundResult queryRefund(String merchantOrderNo, String refundRequestNo);

  record RefundResult(boolean successful, boolean processing, String channelTransactionNo,
                      String channelRefundNo, String message, String rawResponse) {
  }
}
