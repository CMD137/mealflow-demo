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
  Invoke-RestMethod @params
}

function New-AuthHeaders {
  param([string]$Token)
  return @{ Authorization = "Bearer $Token" }
}

$stamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$adminLogin = (Invoke-MealFlow -Method POST -Path "/auth/login" -Body @{
  phone = "13800000000"
  password = "123456"
}).data
$adminHeaders = New-AuthHeaders -Token $adminLogin.token

Write-Host "[mealflow-load] setting merchant $MerchantId limit to $MerchantLimit"
Invoke-MealFlow -Method POST -Path "/queue/merchants/$MerchantId/limit" -Headers $adminHeaders -Body @{
  limit = $MerchantLimit
} | Out-Null

$jobs = for ($i = 1; $i -le $Users; $i++) {
  Start-Job -ArgumentList $BaseUrl, $TimeoutSec, $MerchantId, $stamp, $i -ScriptBlock {
    param($BaseUrl, $TimeoutSec, $MerchantId, $Stamp, $UserNo)

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
      Invoke-RestMethod @params
    }

    try {
      $phone = "137{0:D8}" -f (($Stamp + $UserNo) % 100000000)
      $login = (Invoke-Json -Method POST -Path "/auth/login" -Body @{
        phone = $phone
        password = "123456"
      }).data
      $headers = @{ Authorization = "Bearer $($login.token)" }
      $skuId = if (($UserNo % 2) -eq 0) { 2 } else { 1 }
      $submit = Invoke-Json -Method POST -Path "/orders/submit" -Headers $headers -Body @{
        requestId = "load-order-$Stamp-$UserNo"
        merchantId = $MerchantId
        addressId = 20
        items = @(@{ skuId = $skuId; quantity = 1 })
        remark = "load-peak-orders"
      }
      [pscustomobject]@{
        userNo = $UserNo
        success = $submit.success
        code = $submit.code
        mode = $submit.data.mode
        orderId = $submit.data.orderId
        ticketId = $submit.data.ticketId
      }
    } catch {
      [pscustomobject]@{
        userNo = $UserNo
        success = $false
        code = "EXCEPTION"
        mode = $_.Exception.Message
        orderId = $null
        ticketId = $null
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

if (($results | Where-Object { -not $_.success -and $_.code -eq "EXCEPTION" }).Count -gt 0) {
  throw "Peak order load test has request exceptions"
}
