param(
  [string]$BaseUrl = "http://localhost:8080",
  [int]$Users = 30,
  [long]$MerchantId = 10,
  [int]$MerchantLimit = 5,
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
  $params = @{
    Method = $Method
    Uri = "$BaseUrl$Path"
    TimeoutSec = $TimeoutSec
    Headers = $Headers
  }
  if ($null -ne $Body) {
    $params.ContentType = "application/json"
    $params.Body = ($Body | ConvertTo-Json -Depth 20)
  }
  $response = Invoke-RestMethod @params
  if ($null -ne $response.success -and -not $response.success) {
    throw "Request failed: $Method $Path code=$($response.code) message=$($response.message)"
  }
  return $response
}

function New-AuthHeaders {
  param([string]$Token)
  return @{ Authorization = "Bearer $Token" }
}

$stamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
Invoke-MealFlow -Method POST -Path "/auth/codes" -Body @{ phone = "13800000000" } | Out-Null
$adminLogin = (Invoke-MealFlow -Method POST -Path "/auth/login" -Body @{
  phone = "13800000000"
  code = "123456"
}).data
$adminHeaders = New-AuthHeaders -Token $adminLogin.token
$originalMerchant = (Invoke-MealFlow -Method GET -Path "/merchants/$MerchantId" -Headers $adminHeaders).data
$initialMetrics = (Invoke-MealFlow -Method GET -Path "/queue/merchants/$MerchantId/metrics" -Headers $adminHeaders).data
if ($initialMetrics.waiting -ne 0) {
  throw "Merchant $MerchantId already has waiting tickets; refusing to disturb active queue data"
}
$testCapacity = [int]$initialMetrics.held + $MerchantLimit
$catalogSkus = (Invoke-MealFlow -Method GET -Path "/catalog/merchants/$MerchantId/skus").data
$loadSku = @($catalogSkus | Where-Object {
  $_.status -eq "ON_SHELF" -and $_.stock -ge $Users
} | Sort-Object stock -Descending | Select-Object -First 1)
if ($loadSku.Count -eq 0) {
  throw "Merchant $MerchantId has no on-shelf SKU with at least $Users available stock"
}
$loadSkuId = [long]$loadSku[0].skuId

Write-Host "[mealflow-load] reserving $MerchantLimit additional capacity slots for merchant $MerchantId"
Invoke-MealFlow -Method POST -Path "/merchants/$MerchantId/capacity" -Headers $adminHeaders -Body @{
  baseCapacity = $testCapacity
  manualFactor = 1
} | Out-Null

$results = @()
try {
$jobs = for ($i = 1; $i -le $Users; $i++) {
  Start-Job -ArgumentList $BaseUrl, $TimeoutSec, $MerchantId, $loadSkuId, $stamp, $i -ScriptBlock {
    param($BaseUrl, $TimeoutSec, $MerchantId, $LoadSkuId, $Stamp, $UserNo)

    function Invoke-Json {
      param(
        [string]$Method,
        [string]$Path,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
      )
      $params = @{
        Method = $Method
        Uri = "$BaseUrl$Path"
        TimeoutSec = $TimeoutSec
        Headers = $Headers
      }
      if ($null -ne $Body) {
        $params.ContentType = "application/json"
        $params.Body = ($Body | ConvertTo-Json -Depth 20)
      }
      $response = Invoke-RestMethod @params
      if ($null -ne $response.success -and -not $response.success) {
        throw "Request failed: $Method $Path code=$($response.code) message=$($response.message)"
      }
      return $response
    }

    try {
      $phone = "137{0:D8}" -f (($Stamp + $UserNo) % 100000000)
      Invoke-Json -Method POST -Path "/auth/codes" -Body @{ phone = $phone } | Out-Null
      $login = (Invoke-Json -Method POST -Path "/auth/login" -Body @{
        phone = $phone
        code = "123456"
      }).data
      $headers = @{ Authorization = "Bearer $($login.token)" }
      $address = (Invoke-Json -Method POST -Path "/users/addresses" -Headers $headers -Body @{
        contactName = "压测用户$UserNo"
        phone = $phone
        detail = "压测地址$UserNo号"
      }).data
      $submit = Invoke-Json -Method POST -Path "/orders/submit" -Headers $headers -Body @{
        requestId = "load-order-$Stamp-$UserNo"
        merchantId = $MerchantId
        addressId = $address.addressId
        items = @(@{ skuId = $LoadSkuId; quantity = 1 })
        remark = "load-peak-orders"
      }
      [pscustomobject]@{
        userNo = $UserNo
        success = $submit.success
        code = $submit.code
        mode = $submit.data.mode
        orderId = $submit.data.orderId
        ticketId = $submit.data.ticketId
        token = $login.token
      }
    } catch {
      [pscustomobject]@{
        userNo = $UserNo
        success = $false
        code = "EXCEPTION"
        mode = $_.Exception.Message
        orderId = $null
        ticketId = $null
        token = $null
      }
    }
  }
}

Wait-Job $jobs | Out-Null
$results = $jobs | Receive-Job
$jobs | Remove-Job

$summary = $results | Group-Object mode | Sort-Object Name | ForEach-Object {
  [pscustomobject]@{
    mode = $_.Name
    count = $_.Count
  }
}
$metrics = (Invoke-MealFlow -Method GET -Path "/queue/merchants/$MerchantId/metrics" -Headers $adminHeaders).data

Write-Host "[mealflow-load] peak orders users=$Users merchantId=$MerchantId limit=$MerchantLimit"
$summary | Format-Table -AutoSize
Write-Host "[mealflow-load] queue metrics held=$($metrics.held) waiting=$($metrics.waiting) limit=$($metrics.limit)"

Write-Host "[mealflow-load] cancelling test-created waiting tickets and unpaid orders"
$results | Where-Object { $_.mode -eq "QUEUED" -and $null -ne $_.ticketId } | ForEach-Object {
  Invoke-MealFlow -Method POST -Path "/queue/tickets/$($_.ticketId)/cancel" -Headers (New-AuthHeaders -Token $_.token) | Out-Null
}
$results | Where-Object { $_.mode -eq "ORDER_CREATED" -and $null -ne $_.orderId } | ForEach-Object {
  Invoke-MealFlow -Method POST -Path "/orders/$($_.orderId)/cancel" -Headers (New-AuthHeaders -Token $_.token) -Body @{
    requestId = "load-cleanup-$stamp-$($_.userNo)"
    reason = "LOAD_TEST_CLEANUP"
  } | Out-Null
}
} finally {
  Invoke-MealFlow -Method POST -Path "/merchants/$MerchantId/capacity" -Headers $adminHeaders -Body @{
    baseCapacity = $originalMerchant.baseCapacity
    manualFactor = $originalMerchant.manualFactor
  } | Out-Null
}

$finalMetrics = (Invoke-MealFlow -Method GET -Path "/queue/merchants/$MerchantId/metrics" -Headers $adminHeaders).data
if ($finalMetrics.held -ne $initialMetrics.held -or $finalMetrics.waiting -ne 0) {
  throw "Peak order load test did not release all test-created queue resources"
}

if (($results | Where-Object { -not $_.success -and $_.code -eq "EXCEPTION" }).Count -gt 0) {
  throw "Peak order load test has request exceptions"
}
