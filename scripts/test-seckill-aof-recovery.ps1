param(
  [long]$VoucherId = 1000,
  [int]$ReadyTimeoutSec = 30
)

$ErrorActionPreference = "Stop"
$stockKey = "seckill:{$VoucherId}:stock"
$usersKey = "seckill:{$VoucherId}:users"
$pendingKey = "seckill:{$VoucherId}:pending"

function Read-SeckillState {
  $stock = docker compose exec -T redis redis-cli --raw GET $stockKey
  $users = @(docker compose exec -T redis redis-cli --raw SMEMBERS $usersKey | Sort-Object)
  $pending = @(docker compose exec -T redis redis-cli --raw ZRANGE $pendingKey 0 -1 WITHSCORES)
  return [pscustomobject]@{
    Stock = [string]$stock
    Users = ($users -join "|")
    Pending = ($pending -join "|")
  }
}

$before = Read-SeckillState
if ([string]::IsNullOrWhiteSpace($before.Stock)) {
  throw "Redis stock key $stockKey is missing; create or initialize the voucher before running recovery verification"
}

docker compose restart redis | Out-Null
$deadline = (Get-Date).AddSeconds($ReadyTimeoutSec)
do {
  try {
    $pong = docker compose exec -T redis redis-cli --raw PING
    if ($pong -eq "PONG") {
      break
    }
  } catch {
  }
  Start-Sleep -Milliseconds 500
} while ((Get-Date) -lt $deadline)

if ($pong -ne "PONG") {
  throw "Redis did not become ready after restart"
}

$after = Read-SeckillState
if ($before.Stock -ne $after.Stock -or $before.Users -ne $after.Users -or $before.Pending -ne $after.Pending) {
  throw "AOF recovery mismatch: before=$($before | ConvertTo-Json -Compress) after=$($after | ConvertTo-Json -Compress)"
}

Write-Host "[mealflow-recovery] AOF preserved stock, claimed users and Pending reservations for voucher $VoucherId"
