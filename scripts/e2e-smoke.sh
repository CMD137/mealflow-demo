#!/usr/bin/env bash
# MealFlow container-level E2E smoke (bash + curl + jq).
#
# Mirrors scripts/e2e-smoke.ps1 so the same flow can run inside GitHub Actions after
# `docker compose up -d --build`. Everything goes through the gateway (localhost:8080),
# which is also where the internal HMAC signing is exercised end-to-end.
#
# Usage: bash scripts/e2e-smoke.sh [BASE_URL]
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
STAMP="$(date +%s%3N)"

step() { echo "[mealflow-e2e] $*"; }

fail() { echo "[mealflow-e2e] FAILED: $*" >&2; exit 1; }

# call METHOD PATH [JSON_BODY] [AUTH_TOKEN] -> prints response JSON
call() {
  local method="$1" path="$2" body="${3:-}" token="${4:-}"
  local args=(-sS -X "$method" "${BASE_URL}${path}")
  if [ -n "$token" ]; then args+=(-H "Authorization: Bearer ${token}"); fi
  if [ -n "$body" ]; then args+=(-H "Content-Type: application/json" -d "$body"); fi
  curl "${args[@]}"
}

# call_ok ... -> asserts Result.success == true, prints .data JSON
call_ok() {
  local out
  out="$(call "$@")"
  local success code
  success="$(jq -r '.success' <<<"$out" 2>/dev/null || echo "not-json")"
  code="$(jq -r '.code // empty' <<<"$out" 2>/dev/null || true)"
  if [ "$success" != "true" ]; then
    fail "request $1 $2 -> success=$success code=$code body=$out"
  fi
  jq -c '.data' <<<"$out"
}

# wait_for METHOD PATH AUTH_TOKEN JQ_FILTER TIMEOUT_SECONDS
wait_for() {
  local method="$1" path="$2" token="$3" filter="$4" timeout_sec="$5" out
  local waited=0
  while [ "$waited" -lt "$timeout_sec" ]; do
    out="$(call "$method" "$path" "" "$token")"
    if jq -e "$filter" <<<"$out" >/dev/null 2>&1; then return 0; fi
    sleep 5
    waited=$((waited + 5))
  done
  fail "timeout waiting for $filter on $path, last=$out"
}

step "checking gateway and service pings"
for p in /ping /orders/ping /queue/ping /catalog/ping /vouchers/ping /payments/ping /fulfillment/ping; do
  call_ok GET "$p" >/dev/null
done

step "checking seeded catalog data"
skus="$(call_ok GET /catalog/merchants/10/skus)"
[ "$(jq 'length' <<<"$skus")" -ge 2 ] || fail "expected seeded SKUs for merchant 10"

step "logging in users through auth service"
seckill_phone="139$(printf '%08d' $((STAMP % 100000000)))"
for phone in 13800000000 13800000001 13800000002 "$seckill_phone"; do
  call_ok POST /auth/codes "{\"phone\":\"$phone\"}" >/dev/null
done
admin_login="$(call_ok POST /auth/login '{"phone":"13800000000","code":"123456"}')"
user1_login="$(call_ok POST /auth/login '{"phone":"13800000001","code":"123456"}')"
user2_login="$(call_ok POST /auth/login '{"phone":"13800000002","code":"123456"}')"
seckill_login="$(call_ok POST /auth/login "{\"phone\":\"$seckill_phone\",\"code\":\"123456\"}")"
admin_token="$(jq -r '.token' <<<"$admin_login")"
user1_token="$(jq -r '.token' <<<"$user1_login")"
user2_token="$(jq -r '.token' <<<"$user2_login")"
seckill_token="$(jq -r '.token' <<<"$seckill_login")"
[ "$(jq -r '.roleCode' <<<"$admin_login")" = "MERCHANT_ADMIN" ] || fail "expected demo admin role"

step "creating an isolated seckill voucher"
start_time="$(date -u -d '1 minute ago' +%Y-%m-%dT%H:%M:%S)"
end_time="$(date -u -d '2 hours' +%Y-%m-%dT%H:%M:%S)"
voucher="$(call_ok POST /vouchers/admin "{\"name\":\"E2E秒杀券-$STAMP\",\"type\":\"SECKILL\",\"discountCent\":500,\"stock\":10,\"status\":\"ACTIVE\",\"startTime\":\"$start_time\",\"endTime\":\"$end_time\"}" "$admin_token")"
voucher_id="$(jq -r '.voucherId' <<<"$voucher")"
[ "$voucher_id" != "null" ] || fail "voucher creation failed"

step "claiming seckill voucher through promotion service"
call_ok POST "/vouchers/$voucher_id/seckill" "{\"requestId\":\"e2e-seckill-$STAMP\"}" "$seckill_token" >/dev/null
wait_for GET "/vouchers/$voucher_id/claims/me" "$seckill_token" '.data.status == "CLAIMED"' 60
wallet="$(call_ok GET /vouchers/wallet "" "$seckill_token")"
[ "$(jq "[.[] | select(.voucherId == $voucher_id and .status == \"AVAILABLE\")] | length" <<<"$wallet")" -ge 1 ] || fail "claimed voucher not in wallet"

step "forcing merchant 10 capacity to 1"
tokens="$(call_ok GET /queue/internal/capacity/tokens "" "$admin_token")"
for tid in $(jq -r '.[] | select(.merchantId == 10 and .status == "HELD") | .capacityTokenId' <<<"$tokens"); do
  call_ok POST "/queue/internal/capacity/$tid/release" "{\"requestId\":\"e2e-reset-$STAMP-$tid\",\"reason\":\"E2E_RESET\"}" "$admin_token" >/dev/null
done
call_ok POST /queue/merchants/10/limit '{"limit":1}' "$admin_token" >/dev/null
metrics="$(call_ok GET /queue/merchants/10/metrics "" "$admin_token")"
[ "$(jq -r '.limit' <<<"$metrics")" = "1" ] || fail "merchant queue limit was not updated"

step "submitting first order"
first_submit="$(call_ok POST /orders/submit "{\"requestId\":\"e2e-submit-first-$STAMP\",\"merchantId\":10,\"addressId\":20,\"items\":[{\"skuId\":1,\"quantity\":1}],\"remark\":\"e2e-first\"}" "$user1_token")"
[ "$(jq -r '.mode' <<<"$first_submit")" = "ORDER_CREATED" ] || fail "first order should be created immediately"
first_order_id="$(jq -r '.orderId' <<<"$first_submit")"
pay_order_id="$(jq -r '.payOrderId' <<<"$first_submit")"
[ "$first_order_id" != "null" ] || fail "first orderId missing"
[ "$pay_order_id" != "null" ] || fail "first payOrderId missing"

step "submitting second order and expecting queue"
second_submit="$(call_ok POST /orders/submit "{\"requestId\":\"e2e-submit-second-$STAMP\",\"merchantId\":10,\"addressId\":21,\"items\":[{\"skuId\":2,\"quantity\":1}],\"remark\":\"e2e-second\"}" "$user2_token")"
[ "$(jq -r '.mode' <<<"$second_submit")" = "QUEUED" ] || fail "second order should be queued"
ticket_id="$(jq -r '.ticketId' <<<"$second_submit")"
[ "$ticket_id" != "null" ] || fail "queued ticketId missing"

step "mocking payment and waiting for payment event consumption"
call_ok POST "/payments/internal/$pay_order_id/mock-pay" "" "$admin_token" >/dev/null
call_ok POST /payments/internal/events/dispatch "" "$admin_token" >/dev/null
wait_for GET "/orders/$first_order_id" "$user1_token" '.data.status == "WAIT_MERCHANT_ACCEPT"' 150

step "accepting and marking meal ready"
call_ok POST "/fulfillment/orders/$first_order_id/accept" "{\"requestId\":\"e2e-accept-$STAMP\"}" "$admin_token" >/dev/null
call_ok POST "/fulfillment/orders/$first_order_id/meal-ready" "{\"requestId\":\"e2e-ready-$STAMP\"}" "$admin_token" >/dev/null

step "verifying queued ticket became an order"
ticket="$(call_ok GET "/queue/tickets/$ticket_id" "" "$user2_token")"
[ "$(jq -r '.status' <<<"$ticket")" = "ORDER_CREATED" ] || fail "queued ticket was not converted"
orders="$(call_ok GET /orders "" "$user2_token")"
converted_count="$(jq "[.[] | select(.queueTicketId == $ticket_id)] | length" <<<"$orders")"
[ "$converted_count" -ge 1 ] || fail "converted order was not found in order list"

step "smoke test passed: firstOrder=$first_order_id queuedTicket=$ticket_id"
