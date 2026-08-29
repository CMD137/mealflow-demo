param(
  [string]$BaseUrl = "http://localhost:8080",
  [int]$Users = 20,
  [int]$Stock = 10,
  [int]$TimeoutSec = 10
)

$ErrorActionPreference = "Stop"

function Invoke-Api {
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
    $params.Body = $Body | ConvertTo-Json -Depth 20
  }
  $response = Invoke-RestMethod @params
  if ($null -ne $response.success -and -not $response.success) {
    throw "Request failed: $Method $Path code=$($response.code) message=$($response.message)"
  }
  return $response
}

function Invoke-MySql {
  param([string]$Sql)
  $output = & docker compose exec -T -e MYSQL_PWD=mealflow mysql `
    mysql -uroot -N -B mealflow -e $Sql
  if ($LASTEXITCODE -ne 0) {
    throw "MySQL command failed with exit code $LASTEXITCODE"
  }
  return (($output -join "`n").Trim())
}

function Invoke-Redis {
  param([string[]]$Arguments)
  $output = & docker compose exec -T redis redis-cli --raw @Arguments
  if ($LASTEXITCODE -ne 0) {
    throw "Redis command failed with exit code $LASTEXITCODE"
  }
  return (($output -join "`n").Trim())
}

function Assert-Equal {
  param($Expected, $Actual, [string]$Message)
  if ([string]$Expected -ne [string]$Actual) {
    throw "$Message (expected=$Expected actual=$Actual)"
  }
}

if ($Users -le $Stock) {
  throw "Users must be greater than Stock so the test covers both success and sold-out paths"
}

$stamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$phones = @()
for ($attempt = 0; $attempt -lt 20; $attempt++) {
  $phones = 1..$Users | ForEach-Object { "139{0:D8}" -f (($stamp + $_) % 100000000) }
  $quotedPhones = ($phones | ForEach-Object { "'$_'" }) -join ","
  if ([int](Invoke-MySql "SELECT COUNT(*) FROM user_account WHERE phone IN ($quotedPhones);") -eq 0) {
    break
  }
  $stamp += $Users + 1
}
if ([int](Invoke-MySql "SELECT COUNT(*) FROM user_account WHERE phone IN ($quotedPhones);") -ne 0) {
  throw "Could not allocate an unused test phone range"
}

$voucherId = $null
try {
  Write-Host "[mealflow-seckill-mysql] checking gateway"
  Invoke-Api -Method GET -Path "/ping" | Out-Null

  Invoke-Api -Method POST -Path "/auth/codes" -Body @{ phone = "13800000000" } | Out-Null
  $admin = (Invoke-Api -Method POST -Path "/auth/login" -Body @{
    phone = "13800000000"
    code = "123456"
  }).data
  $adminHeaders = @{ Authorization = "Bearer $($admin.token)" }

  Write-Host "[mealflow-seckill-mysql] creating isolated voucher stock=$Stock users=$Users"
  $voucher = (Invoke-Api -Method POST -Path "/vouchers/admin" -Headers $adminHeaders -Body @{
    name = "MySQL并发验收券-$stamp"
    type = "SECKILL"
    discountCent = 300
    stock = $Stock
    status = "ACTIVE"
    startTime = (Get-Date).AddMinutes(-1).ToString("yyyy-MM-ddTHH:mm:ss")
    endTime = (Get-Date).AddHours(1).ToString("yyyy-MM-ddTHH:mm:ss")
  }).data
  $voucherId = [long]$voucher.voucherId

  Write-Host "[mealflow-seckill-mysql] sending concurrent requests through gateway, Redis, RocketMQ and MySQL"
  & "$PSScriptRoot/load-seckill.ps1" -BaseUrl $BaseUrl -Users $Users -VoucherId $voucherId `
    -TimeoutSec $TimeoutSec -ExpectedClaimed $Stock -ExpectedSoldOut ($Users - $Stock) -RunStamp $stamp
  if ($LASTEXITCODE -ne 0) {
    throw "Concurrent load script failed with exit code $LASTEXITCODE"
  }

  $dbStock = Invoke-MySql "SELECT stock FROM voucher WHERE id=$voucherId;"
  $claimed = Invoke-MySql "SELECT COUNT(*) FROM voucher_claim WHERE voucher_id=$voucherId AND status='CLAIMED';"
  $mysqlSoldOut = Invoke-MySql "SELECT COUNT(*) FROM voucher_claim WHERE voucher_id=$voucherId AND status='SOLD_OUT';"
  $walletCount = Invoke-MySql "SELECT COUNT(*) FROM user_voucher WHERE voucher_id=$voucherId;"
  $redisStock = Invoke-Redis @("GET", "seckill:{$voucherId}:stock")
  $redisUsers = Invoke-Redis @("SCARD", "seckill:{$voucherId}:users")
  $redisPending = Invoke-Redis @("ZCARD", "seckill:{$voucherId}:pending")

  Assert-Equal 0 $dbStock "MySQL stock did not converge to zero"
  Assert-Equal $Stock $claimed "Unexpected CLAIMED claim count"
  # Redis rejects requests after its stock reaches zero, so normal sold-out requests
  # never enter MQ/MySQL and must not create voucher_claim rows.
  Assert-Equal 0 $mysqlSoldOut "Redis-rejected sold-out requests unexpectedly reached MySQL"
  Assert-Equal $Stock $walletCount "Unexpected user_voucher count"
  Assert-Equal 0 $redisStock "Redis stock did not converge to zero"
  Assert-Equal $Stock $redisUsers "Redis claimed-user set did not match issued vouchers"
  Assert-Equal 0 $redisPending "Redis Pending was not fully settled"

  $claimedPhone = Invoke-MySql @"
SELECT ua.phone
FROM voucher_claim vc
JOIN user_account ua ON ua.id = vc.user_id
WHERE vc.voucher_id=$voucherId AND vc.status='CLAIMED'
ORDER BY vc.id
LIMIT 1;
"@
  Invoke-Api -Method POST -Path "/auth/codes" -Body @{ phone = $claimedPhone } | Out-Null
  $claimedLogin = (Invoke-Api -Method POST -Path "/auth/login" -Body @{
    phone = $claimedPhone
    code = "123456"
  }).data
  $duplicate = (Invoke-Api -Method POST -Path "/vouchers/$voucherId/seckill" `
    -Headers @{ Authorization = "Bearer $($claimedLogin.token)" } `
    -Body @{ requestId = "mysql-duplicate-$stamp" }).data
  Assert-Equal "ALREADY_CLAIMED" $duplicate.status "Repeated claim was not recognized from durable state"
  Assert-Equal 0 (Invoke-Redis @("GET", "seckill:{$voucherId}:stock")) `
    "Repeated claim changed Redis stock"

  Write-Host "[mealflow-seckill-mysql] passed voucherId=$voucherId claimed=$claimed soldOutAtRedis=$($Users - $Stock) pending=$redisPending"
} finally {
  if ($null -ne $voucherId) {
    Invoke-Redis @("DEL", "seckill:{$voucherId}:stock", "seckill:{$voucherId}:users",
      "seckill:{$voucherId}:pending") | Out-Null
    Invoke-MySql "DELETE FROM voucher_claim_retry WHERE voucher_id=$voucherId; DELETE FROM voucher_claim WHERE voucher_id=$voucherId; DELETE FROM user_voucher WHERE voucher_id=$voucherId; DELETE FROM voucher WHERE id=$voucherId;" | Out-Null
  }
  Invoke-MySql "DELETE FROM auth_token WHERE user_id IN (SELECT id FROM user_account WHERE phone IN ($quotedPhones)); DELETE FROM user_account WHERE phone IN ($quotedPhones);" | Out-Null
}
