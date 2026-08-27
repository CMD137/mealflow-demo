param(
  [string]$BaseUrl = "http://localhost:8080",
  [string]$TestCode = "123456",
  [int]$RecoveryTimeoutSec = 90
)

$ErrorActionPreference = "Stop"

function Invoke-Api {
  param([string]$Method, [string]$Path, [object]$Body = $null, [hashtable]$Headers = @{})
  $params = @{ Method = $Method; Uri = "$BaseUrl$Path"; Headers = $Headers; TimeoutSec = 20 }
  if ($null -ne $Body) {
    $params.ContentType = "application/json"
    $params.Body = $Body | ConvertTo-Json -Depth 10
  }
  return Invoke-RestMethod @params
}

function Login-TestUser {
  param([string]$Phone)
  Invoke-Api -Method POST -Path "/auth/codes" -Body @{ phone = $Phone } | Out-Null
  return (Invoke-Api -Method POST -Path "/auth/login" -Body @{ phone = $Phone; code = $TestCode }).data
}

$stamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$admin = Login-TestUser -Phone "13800000000"
$user = Login-TestUser -Phone ("137{0:D8}" -f ($stamp % 100000000))
$adminHeaders = @{ Authorization = "Bearer $($admin.token)" }
$userHeaders = @{ Authorization = "Bearer $($user.token)" }
$voucher = (Invoke-Api -Method POST -Path "/vouchers/admin" -Headers $adminHeaders -Body @{
  name = "Pending恢复券-$stamp"
  type = "SECKILL"
  discountCent = 500
  stock = 1
  status = "ACTIVE"
  startTime = (Get-Date).AddMinutes(-1).ToString("yyyy-MM-ddTHH:mm:ss")
  endTime = (Get-Date).AddHours(1).ToString("yyyy-MM-ddTHH:mm:ss")
}).data
$voucherId = $voucher.voucherId

docker compose stop rocketmq-broker | Out-Null
try {
  $claim = (Invoke-Api -Method POST -Path "/vouchers/$voucherId/seckill" -Headers $userHeaders -Body @{
    requestId = "pending-recovery-$stamp"
  }).data
  if ($claim.status -ne "PENDING") {
    throw "Expected PENDING while RocketMQ is unavailable, got $($claim.status)"
  }

  $pending = [int](docker compose exec -T redis redis-cli --raw ZCARD "seckill:{$voucherId}:pending")
  if ($pending -ne 1) {
    throw "Expected one Pending reservation before recovery, got $pending"
  }

  & "$PSScriptRoot/test-seckill-aof-recovery.ps1" -VoucherId $voucherId
} finally {
  docker compose start rocketmq-broker | Out-Null
}

$deadline = (Get-Date).AddSeconds($RecoveryTimeoutSec)
do {
  Start-Sleep -Seconds 1
  $status = (Invoke-Api -Method GET -Path "/vouchers/$voucherId/claims/me" -Headers $userHeaders).data
  if ($status.status -eq "CLAIMED") {
    break
  }
} while ((Get-Date) -lt $deadline)

if ($status.status -ne "CLAIMED") {
  throw "Pending reservation was not recovered before timeout; final status=$($status.status)"
}

$pending = [int](docker compose exec -T redis redis-cli --raw ZCARD "seckill:{$voucherId}:pending")
if ($pending -ne 0) {
  throw "Expected Pending to be empty after settlement, got $pending"
}

Write-Host "[mealflow-recovery] MQ outage reservation recovered and settled for voucher $voucherId"
