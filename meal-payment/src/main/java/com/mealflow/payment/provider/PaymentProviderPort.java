package com.mealflow.payment.provider;

import java.util.Map;

public interface PaymentProviderPort {
  String code();
  String checkoutUrl(long payOrderId, int amountCent);
  boolean verifyCallback(Map<String, String> parameters);
}
