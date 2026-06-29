INSERT IGNORE INTO notify_template (
  template_code, biz_type, content_template, channels, enabled, create_time, update_time
)
VALUES
  ('ORDER_PAID', 'ORDER', 'Order {{orderId}} has been paid and is waiting for merchant acceptance.', 'IN_APP,SMS_MOCK', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('ORDER_MEAL_READY', 'ORDER', 'Order {{orderId}} is ready for pickup.', 'IN_APP,SMS_MOCK', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  ('ORDER_DELIVERED', 'ORDER', 'Order {{orderId}} has been delivered.', 'IN_APP', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
