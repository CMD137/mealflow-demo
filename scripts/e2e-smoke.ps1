param(
  [string]$BaseUrl = "http://localhost:8080",
  [int]$TimeoutSec = 10,
  [string]$InternalSecret = "mealflow-dev-internal-secret"
)

$ErrorActionPreference = "Stop"

function Invoke-MealFlow {
  param(
    [ValidateSet("GET", "POST")]
    [string]$Method,
    [string]$Path,
    [object]$Body = $null,
    [hashtable]$Headers = @{}
  )

  $uri = "$BaseUrl$Path"
  $params = @{
    Method = $Method
    Uri = $uri
    TimeoutSec = $TimeoutSec
    Headers = $Headers
  }

  if ($null -ne $Body) {
    $params.ContentType = "application/json"
    $params.Body = ($Body | ConvertTo-Json -Depth 20)
  }

  $attempts = 0
  do {
    $attempts++
    try {
      $response = Invoke-RestMethod @params
      break
    } catch {
      $statusCode = $null
      if ($_.Exception.Response) {
        $statusCode = [int]$_.Exception.Response.StatusCode
      }
      if ($attempts -ge 12 -or ($null -ne $statusCode -and $statusCode -notin 502, 503, 504)) {
        throw
      }
      Start-Sleep -Seconds 5
    }
  } while ($true)

  if ($null -ne $response.success -and -not $response.success) {
    throw "Request failed: $Method $Path code=$($response.code) message=$($response.message)"
  }
  return $response
}

function Invoke-MealFlowInternal {
  param(
    [ValidateSet("GET", "POST")]
    [string]$Method,
    [ValidateSet("meal-queue", "meal-payment")]
    [string]$Service,
    [int]$Port,
    [string]$Path,
    [object]$Body = $null
  )

  $bodyJson = if ($null -eq $Body) { "" } else { $Body | ConvertTo-Json -Depth 20 -Compress }
  $bodyBase64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($bodyJson))
  $python = @'
import base64, hashlib, hmac, json, os, time, urllib.error, urllib.request, uuid
method = os.environ["MF_METHOD"]
service = os.environ["MF_SERVICE"]
port = os.environ["MF_PORT"]
path = os.environ["MF_PATH"]
secret = os.environ["MF_SECRET"]
body = base64.b64decode(os.environ["MF_BODY_B64"])
timestamp = str(int(time.time() * 1000))
nonce = uuid.uuid4().hex
canonical = "\n".join(["mealflow-e2e", method, path, "", timestamp, nonce])
signature = hmac.new(secret.encode(), canonical.encode(), hashlib.sha256).hexdigest()
headers = {
    "X-Internal-Service": "mealflow-e2e",
    "X-Internal-Timestamp": timestamp,
    "X-Internal-Nonce": nonce,
    "X-Internal-Signature": signature,
    "Accept": "application/json",
}
if body:
    headers["Content-Type"] = "application/json"
request = urllib.request.Request("http://" + service + ":" + port + path, data=body or None,
    headers=headers, method=method)
try:
    with urllib.request.urlopen(request, timeout=20) as response:
        print(response.read().decode())
except urllib.error.HTTPError as error:
    print(error.read().decode())
'@
  $output = & docker compose exec -T `
    -e "MF_METHOD=$Method" `
    -e "MF_SERVICE=$Service" `
    -e "MF_PORT=$Port" `
    -e "MF_PATH=$Path" `
    -e "MF_SECRET=$InternalSecret" `
    -e "MF_BODY_B64=$bodyBase64" `
    meal-support-agent-runtime python -c $python
  $response = $output | ConvertFrom-Json
  if ($null -ne $response.success -and -not $response.success) {
    throw "Internal request failed: $Method $Path code=$($response.code) message=$($response.message)"
  }
  return $response
}

function Assert-True {
  param(
    [bool]$Condition,
    [string]$Message
  )
  if (-not $Condition) {
    throw $Message
  }
}

function Step {
  param([string]$Message)
  Write-Host "[mealflow-e2e] $Message"
}

function New-AuthHeaders {
  param([string]$Token)
  return @{ Authorization = "Bearer $Token" }
}

$stamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()

Step "checking gateway and service pings"
@(
  "/ping",
  "/orders/ping",
  "/queue/ping",
  "/catalog/ping",
  "/vouchers/ping",
  "/payments/ping",
  "/fulfillment/ping"
) | ForEach-Object {
  Invoke-MealFlow -Method GET -Path $_ | Out-Null
}

Step "checking seeded catalog data"
$skus = (Invoke-MealFlow -Method GET -Path "/catalog/merchants/10/skus").data
Assert-True ($skus.Count -ge 2) "Expected seeded SKUs for merchant 10"

Step "logging in users through auth service"
$seckillPhone = "139{0:D8}" -f ($stamp % 100000000)
$loginPhones = @(
  "13800000000",
  "13800000001",
  "13800000002",
  $seckillPhone
)
$loginPhones | ForEach-Object {
  Invoke-MealFlow -Method POST -Path "/auth/codes" -Body @{ phone = $_ } | Out-Null
}
$adminLogin = (Invoke-MealFlow -Method POST -Path "/auth/login" -Body @{
  phone = "13800000000"
  code = "123456"
}).data
$firstUserLogin = (Invoke-MealFlow -Method POST -Path "/auth/login" -Body @{
  phone = "13800000001"
  code = "123456"
}).data
$secondUserLogin = (Invoke-MealFlow -Method POST -Path "/auth/login" -Body @{
  phone = "13800000002"
  code = "123456"
}).data
$seckillLogin = (Invoke-MealFlow -Method POST -Path "/auth/login" -Body @{
  phone = $seckillPhone
  code = "123456"
}).data
$adminHeaders = New-AuthHeaders -Token $adminLogin.token
$firstUserHeaders = New-AuthHeaders -Token $firstUserLogin.token
$secondUserHeaders = New-AuthHeaders -Token $secondUserLogin.token
$seckillHeaders = New-AuthHeaders -Token $seckillLogin.token
Assert-True ($adminLogin.roleCode -eq "MERCHANT_ADMIN") "Expected demo admin to have merchant admin role"

function Resolve-TestAddressId {
  param([hashtable]$Headers, [string]$UserLabel)
  $addresses = (Invoke-MealFlow -Method GET -Path "/users/addresses" -Headers $Headers).data
  $address = @($addresses | Where-Object { $_.defaultAddress } | Select-Object -First 1)
  if ($address.Count -eq 0) { $address = @($addresses | Select-Object -First 1) }
  if ($address.Count -eq 0) { throw "测试用户 $UserLabel 缺少收货地址" }
  return [long]$address[0].addressId
}

$firstAddressId = Resolve-TestAddressId -Headers $firstUserHeaders -UserLabel "101"
$secondAddressId = Resolve-TestAddressId -Headers $secondUserHeaders -UserLabel "102"

Step "creating an isolated seckill voucher"
$testVoucher = (Invoke-MealFlow -Method POST -Path "/vouchers/admin" -Headers $adminHeaders -Body @{
  name = "E2E秒杀券-$stamp"
  type = "SECKILL"
  discountCent = 500
  stock = 10
  status = "ACTIVE"
  startTime = (Get-Date).AddMinutes(-1).ToString("yyyy-MM-ddTHH:mm:ss")
  endTime = (Get-Date).AddHours(1).ToString("yyyy-MM-ddTHH:mm:ss")
}).data
$testVoucherId = $testVoucher.voucherId

Step "claiming seckill voucher through promotion service"
$seckill = (Invoke-MealFlow -Method POST -Path "/vouchers/$testVoucherId/seckill" -Body @{
  requestId = "e2e-seckill-$stamp"
} -Headers $seckillHeaders).data
Assert-True ($seckill.status -eq "PENDING" -or $seckill.status -eq "DUPLICATE") "Expected seckill request to be accepted"
for ($claimAttempt = 1; $claimAttempt -le 20; $claimAttempt++) {
  $seckill = (Invoke-MealFlow -Method GET -Path "/vouchers/$testVoucherId/claims/me" -Headers $seckillHeaders).data
  if ($seckill.status -eq "CLAIMED") {
    break
  }
  Start-Sleep -Seconds 1
}
Assert-True ($seckill.status -eq "CLAIMED") "Expected pending seckill claim to settle"
$wallet = (Invoke-MealFlow -Method GET -Path "/vouchers/wallet" -Headers $seckillHeaders).data
$claimedVoucher = @($wallet | Where-Object { $_.voucherId -eq $testVoucherId -and $_.status -eq "AVAILABLE" })
Assert-True ($claimedVoucher.Count -ge 1) "Claimed voucher was not found in wallet"

Step "forcing merchant 10 capacity to 1"
for ($resetRound = 1; $resetRound -le 20; $resetRound++) {
  $tokens = (Invoke-MealFlowInternal -Method GET -Service meal-queue -Port 8106 -Path "/queue/internal/capacity/tokens").data
  $heldTokens = @($tokens | Where-Object { $_.merchantId -eq 10 -and $_.status -eq "HELD" })
  if ($heldTokens.Count -eq 0) {
    break
  }
  $heldTokens | ForEach-Object {
    Invoke-MealFlowInternal -Method POST -Service meal-queue -Port 8106 -Path "/queue/internal/capacity/$($_.capacityTokenId)/release" -Body @{
      requestId = "e2e-reset-$stamp-$resetRound-$($_.capacityTokenId)"
      reason = "E2E_RESET"
    } -Headers $adminHeaders | Out-Null
  }
}
$tokens = (Invoke-MealFlowInternal -Method GET -Service meal-queue -Port 8106 -Path "/queue/internal/capacity/tokens").data
$heldTokens = @($tokens | Where-Object { $_.merchantId -eq 10 -and $_.status -eq "HELD" })
if ($heldTokens.Count -gt 0) {
  throw "Unable to reset merchant 10 held capacity tokens"
}
Invoke-MealFlow -Method POST -Path "/merchants/10/capacity" -Body @{ baseCapacity = 1; manualFactor = 1 } -Headers $adminHeaders | Out-Null

$firstRequestId = "e2e-submit-first-$stamp"
$secondRequestId = "e2e-submit-second-$stamp"

$firstOrderBody = @{
  requestId = $firstRequestId
  merchantId = 10
  addressId = $firstAddressId
  items = @(@{ skuId = 1; quantity = 1 })
  remark = "e2e-first"
}

$secondOrderBody = @{
  requestId = $secondRequestId
  merchantId = 10
  addressId = $secondAddressId
  items = @(@{ skuId = 2; quantity = 1 })
  remark = "e2e-second"
}

Step "submitting first order"
$firstSubmit = (Invoke-MealFlow -Method POST -Path "/orders/submit" -Body $firstOrderBody -Headers $firstUserHeaders).data
Assert-True ($firstSubmit.mode -eq "ORDER_CREATED") "First order should be created immediately"
Assert-True ($null -ne $firstSubmit.orderId) "First orderId is missing"
Assert-True ($null -ne $firstSubmit.payOrderId) "First payOrderId is missing"

Step "submitting second order and expecting queue"
$secondSubmit = (Invoke-MealFlow -Method POST -Path "/orders/submit" -Body $secondOrderBody -Headers $secondUserHeaders).data
Assert-True ($secondSubmit.mode -eq "QUEUED") "Second order should be queued"
Assert-True ($null -ne $secondSubmit.ticketId) "Queued ticketId is missing"

Step "mocking payment and waiting for payment event consumption"
Invoke-MealFlowInternal -Method POST -Service meal-payment -Port 8108 -Path "/payments/internal/$($firstSubmit.payOrderId)/mock-pay" | Out-Null
Invoke-MealFlowInternal -Method POST -Service meal-payment -Port 8108 -Path "/payments/internal/events/dispatch" | Out-Null
for ($paidAttempt = 1; $paidAttempt -le 24; $paidAttempt++) {
  $paidOrder = (Invoke-MealFlow -Method GET -Path "/orders/$($firstSubmit.orderId)" -Headers $firstUserHeaders).data
  if ($paidOrder.status -eq "WAIT_MERCHANT_ACCEPT") {
    break
  }
  Start-Sleep -Seconds 5
}
$paidOrder = (Invoke-MealFlow -Method GET -Path "/orders/$($firstSubmit.orderId)" -Headers $firstUserHeaders).data
Assert-True ($paidOrder.status -eq "WAIT_MERCHANT_ACCEPT") "PaymentPaid event was not consumed by order-service"

Step "accepting and marking meal ready"
Invoke-MealFlow -Method POST -Path "/fulfillment/orders/$($firstSubmit.orderId)/accept" -Body @{ requestId = "e2e-accept-$stamp" } -Headers $adminHeaders | Out-Null
Invoke-MealFlow -Method POST -Path "/fulfillment/orders/$($firstSubmit.orderId)/meal-ready" -Body @{ requestId = "e2e-ready-$stamp" } -Headers $adminHeaders | Out-Null

Step "verifying queued ticket became an order"
$ticket = (Invoke-MealFlow -Method GET -Path "/queue/tickets/$($secondSubmit.ticketId)" -Headers $secondUserHeaders).data
Assert-True ($ticket.status -eq "ORDER_CREATED") "Queued ticket was not converted to ORDER_CREATED"

$orders = (Invoke-MealFlow -Method GET -Path "/orders" -Headers $secondUserHeaders).data
$converted = @($orders | Where-Object { $_.queueTicketId -eq $secondSubmit.ticketId })
Assert-True ($converted.Count -ge 1) "Converted order was not found in order list"

Step "smoke test passed: firstOrder=$($firstSubmit.orderId), queuedTicket=$($secondSubmit.ticketId), convertedOrder=$($converted[0].orderId)"
