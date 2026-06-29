param(
  [ValidateSet("Auth", "RedisRebuild", "DuplicateRelease", "All")]
  [string]$Scenario = "Auth",
  [string]$BaseUrl = "http://localhost:8080",
  [long]$MerchantId = 10,
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

function Get-AdminHeaders {
  $login = (Invoke-MealFlow -Method POST -Path "/auth/login" -Body @{
    phone = "13800000000"
    password = "123456"
  }).data
  New-AuthHeaders -Token $login.token
}

function Invoke-AuthDemo {
  Write-Host "[mealflow-fault] auth demo"
  try {
    Invoke-MealFlow -Method GET -Path "/users/me" | Out-Null
    throw "Expected /users/me without token to fail"
  } catch {
    Write-Host "[mealflow-fault] unauthenticated /users/me was rejected"
  }
  $headers = Get-AdminHeaders
  $me = (Invoke-MealFlow -Method GET -Path "/users/me" -Headers $headers).data
  Write-Host "[mealflow-fault] authenticated user=$($me.userId)"
}

function Invoke-RedisRebuildDemo {
  Write-Host "[mealflow-fault] redis hot-index rebuild demo"
  $headers = Get-AdminHeaders
  $before = (Invoke-MealFlow -Method GET -Path "/queue/merchants/$MerchantId/metrics" -Headers $headers).data
  docker exec mealflow-redis redis-cli DEL "mealflow:queue:waiting:$MerchantId" "capacity:merchant:$MerchantId:inflight" | Out-Null
  docker compose restart meal-queue | Out-Null
  Start-Sleep -Seconds 12
  $after = (Invoke-MealFlow -Method GET -Path "/queue/merchants/$MerchantId/metrics" -Headers $headers).data
  Write-Host "[mealflow-fault] before held=$($before.held) waiting=$($before.waiting); after held=$($after.held) waiting=$($after.waiting)"
}

function Invoke-DuplicateReleaseDemo {
  Write-Host "[mealflow-fault] duplicate capacity release demo"
  $headers = Get-AdminHeaders
  $tokens = (Invoke-MealFlow -Method GET -Path "/queue/internal/capacity/tokens" -Headers $headers).data
  $held = @($tokens | Where-Object { $_.merchantId -eq $MerchantId -and $_.status -eq "HELD" } | Select-Object -First 1)
  if ($held.Count -eq 0) {
    throw "No HELD capacity token found for merchant $MerchantId. Run scripts/e2e-smoke.ps1 or scripts/load-peak-orders.ps1 first."
  }
  $first = Invoke-MealFlow -Method POST -Path "/queue/internal/capacity/$($held[0].capacityTokenId)/release" -Headers $headers -Body @{
    requestId = "fault-release-first-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"
    reason = "FAULT_DUPLICATE_RELEASE"
  }
  $second = Invoke-MealFlow -Method POST -Path "/queue/internal/capacity/$($held[0].capacityTokenId)/release" -Headers $headers -Body @{
    requestId = "fault-release-second-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"
    reason = "FAULT_DUPLICATE_RELEASE"
  }
  Write-Host "[mealflow-fault] first released=$($first.data.released); second released=$($second.data.released)"
}

if ($Scenario -eq "Auth" -or $Scenario -eq "All") {
  Invoke-AuthDemo
}
if ($Scenario -eq "RedisRebuild" -or $Scenario -eq "All") {
  Invoke-RedisRebuildDemo
}
if ($Scenario -eq "DuplicateRelease" -or $Scenario -eq "All") {
  Invoke-DuplicateReleaseDemo
}
