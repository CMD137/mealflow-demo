package com.mealflow.payment;

import com.mealflow.common.api.ErrorCode;
import com.mealflow.common.exception.BizException;
import com.mealflow.common.status.PaymentStatus;
import com.mealflow.infra.id.IdGenerator;
import com.mealflow.infra.idempotent.IdempotentTemplate;
import com.mealflow.payment.api.CreatePaymentRequest;
import com.mealflow.payment.api.PaymentView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
  private final IdGenerator idGenerator = new IdGenerator();
  private final IdempotentTemplate idempotentTemplate = new IdempotentTemplate();
  private final JdbcTemplate jdbcTemplate;

  public PaymentService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional
  public PaymentView create(CreatePaymentRequest request) {
    return idempotentTemplate.execute("payment:create:" + request.requestId(), () -> {
      long id = idGenerator.next("paymentOrder");
      LocalDateTime now = LocalDateTime.now();
      jdbcTemplate.update("""
              INSERT INTO payment_order (id, order_id, amount_cent, status, create_time, update_time)
              VALUES (?, ?, ?, ?, ?, ?)
              """,
          id, request.orderId(), request.amountCent(), PaymentStatus.UNPAID.name(), now, now);
      return requirePayment(id);
    });
  }

  @Transactional
  public PaymentView mockPay(long payOrderId) {
    PaymentView payment = requirePayment(payOrderId);
    PaymentStatus status = PaymentStatus.valueOf(payment.status());
    if (status == PaymentStatus.PAID) {
      return payment;
    }
    if (status != PaymentStatus.UNPAID && status != PaymentStatus.PAYING) {
      throw new BizException(ErrorCode.ILLEGAL_STATUS, "payment order is not payable");
    }
    jdbcTemplate.update("""
            UPDATE payment_order
            SET status = ?, update_time = ?
            WHERE id = ? AND status IN (?, ?)
            """,
        PaymentStatus.PAID.name(), LocalDateTime.now(), payOrderId,
        PaymentStatus.UNPAID.name(), PaymentStatus.PAYING.name());
    return requirePayment(payOrderId);
  }

  @Transactional
  public void close(long payOrderId) {
    requirePayment(payOrderId);
    jdbcTemplate.update("""
            UPDATE payment_order
            SET status = ?, update_time = ?
            WHERE id = ? AND status IN (?, ?)
            """,
        PaymentStatus.CLOSED.name(), LocalDateTime.now(), payOrderId,
        PaymentStatus.UNPAID.name(), PaymentStatus.PAYING.name());
  }

  public PaymentView get(long payOrderId) {
    return requirePayment(payOrderId);
  }

  public List<PaymentView> list() {
    return jdbcTemplate.query("""
            SELECT id, order_id, amount_cent, status
            FROM payment_order
            ORDER BY id
            """,
        this::mapPayment);
  }

  private PaymentView requirePayment(long payOrderId) {
    List<PaymentView> payments = jdbcTemplate.query("""
            SELECT id, order_id, amount_cent, status
            FROM payment_order
            WHERE id = ?
            """,
        this::mapPayment, payOrderId);
    if (payments.isEmpty()) {
      throw new BizException(ErrorCode.NOT_FOUND, "payment order not found");
    }
    return payments.get(0);
  }

  private PaymentView mapPayment(ResultSet rs, int rowNum) throws SQLException {
    return new PaymentView(rs.getLong("id"), rs.getLong("order_id"),
        rs.getInt("amount_cent"), rs.getString("status"));
  }
}
