param(
  [string]$BaseUrl = "http://localhost:8080",
  [int]$TimeoutSec = 10
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
      if ($attempts -ge 12) {
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

Step "forcing merchant 10 capacity to 1"
for ($resetRound = 1; $resetRound -le 20; $resetRound++) {
  $tokens = (Invoke-MealFlow -Method GET -Path "/queue/internal/capacity/tokens").data
  $heldTokens = @($tokens | Where-Object { $_.merchantId -eq 10 -and $_.status -eq "HELD" })
  if ($heldTokens.Count -eq 0) {
    break
  }
  $heldTokens | ForEach-Object {
    Invoke-MealFlow -Method POST -Path "/queue/internal/capacity/$($_.capacityTokenId)/release" -Body @{
      requestId = "e2e-reset-$stamp-$resetRound-$($_.capacityTokenId)"
      reason = "E2E_RESET"
    } | Out-Null
  }
}
$tokens = (Invoke-MealFlow -Method GET -Path "/queue/internal/capacity/tokens").data
$heldTokens = @($tokens | Where-Object { $_.merchantId -eq 10 -and $_.status -eq "HELD" })
if ($heldTokens.Count -gt 0) {
  throw "Unable to reset merchant 10 held capacity tokens"
}
Invoke-MealFlow -Method POST -Path "/queue/merchants/10/limit" -Body @{ limit = 1 } | Out-Null
$metrics = (Invoke-MealFlow -Method GET -Path "/queue/merchants/10/metrics").data
Assert-True ([int]$metrics.limit -eq 1) "Merchant queue limit was not updated"

$firstRequestId = "e2e-submit-first-$stamp"
$secondRequestId = "e2e-submit-second-$stamp"

$firstOrderBody = @{
  requestId = $firstRequestId
  merchantId = 10
  addressId = 20
  items = @(@{ skuId = 1; quantity = 1 })
  remark = "e2e-first"
}

$secondOrderBody = @{
  requestId = $secondRequestId
  merchantId = 10
  addressId = 21
  items = @(@{ skuId = 2; quantity = 1 })
  remark = "e2e-second"
}

Step "submitting first order"
$firstSubmit = (Invoke-MealFlow -Method POST -Path "/orders/submit" -Body $firstOrderBody -Headers @{ "X-User-Id" = "101" }).data
Assert-True ($firstSubmit.mode -eq "ORDER_CREATED") "First order should be created immediately"
Assert-True ($null -ne $firstSubmit.orderId) "First orderId is missing"
Assert-True ($null -ne $firstSubmit.payOrderId) "First payOrderId is missing"

Step "submitting second order and expecting queue"
$secondSubmit = (Invoke-MealFlow -Method POST -Path "/orders/submit" -Body $secondOrderBody -Headers @{ "X-User-Id" = "102" }).data
Assert-True ($secondSubmit.mode -eq "QUEUED") "Second order should be queued"
Assert-True ($null -ne $secondSubmit.ticketId) "Queued ticketId is missing"

Step "mocking payment and marking order paid"
Invoke-MealFlow -Method POST -Path "/payments/$($firstSubmit.payOrderId)/mock-pay" | Out-Null
Invoke-MealFlow -Method POST -Path "/orders/$($firstSubmit.orderId)/pay-success" | Out-Null

Step "accepting and marking meal ready"
Invoke-MealFlow -Method POST -Path "/fulfillment/orders/$($firstSubmit.orderId)/accept" -Body @{ requestId = "e2e-accept-$stamp" } | Out-Null
Invoke-MealFlow -Method POST -Path "/fulfillment/orders/$($firstSubmit.orderId)/meal-ready" -Body @{ requestId = "e2e-ready-$stamp" } | Out-Null

Step "verifying queued ticket became an order"
$ticket = (Invoke-MealFlow -Method GET -Path "/queue/tickets/$($secondSubmit.ticketId)").data
Assert-True ($ticket.status -eq "ORDER_CREATED") "Queued ticket was not converted to ORDER_CREATED"

$orders = (Invoke-MealFlow -Method GET -Path "/orders").data
$converted = @($orders | Where-Object { $_.queueTicketId -eq $secondSubmit.ticketId })
Assert-True ($converted.Count -ge 1) "Converted order was not found in order list"

Step "smoke test passed: firstOrder=$($firstSubmit.orderId), queuedTicket=$($secondSubmit.ticketId), convertedOrder=$($converted[0].orderId)"
