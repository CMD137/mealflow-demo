param(
  [string]$BaseUrl = "http://localhost:8080",
  [int]$Users = 50,
  [long]$VoucherId = 1000,
  [int]$TimeoutSec = 10,
  [int]$ExpectedClaimed = -1
)

$ErrorActionPreference = "Stop"
$stamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()

$jobs = for ($i = 1; $i -le $Users; $i++) {
  Start-Job -ArgumentList $BaseUrl, $TimeoutSec, $VoucherId, $stamp, $i -ScriptBlock {
    param($BaseUrl, $TimeoutSec, $VoucherId, $Stamp, $UserNo)

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
      $phone = "139{0:D8}" -f (($Stamp + $UserNo) % 100000000)
      $login = (Invoke-Json -Method POST -Path "/auth/login" -Body @{
        phone = $phone
        password = "123456"
      }).data
      $headers = @{ Authorization = "Bearer $($login.token)" }
      $claim = Invoke-Json -Method POST -Path "/vouchers/$VoucherId/seckill" -Headers $headers -Body @{
        requestId = "load-seckill-$Stamp-$UserNo"
      }
      if ($claim.data.status -eq "PENDING") {
        for ($attempt = 1; $attempt -le 20; $attempt++) {
          Start-Sleep -Milliseconds 500
          $claim = Invoke-Json -Method GET -Path "/vouchers/$VoucherId/claims/me" -Headers $headers
          if ($claim.data.status -ne "PENDING") {
            break
          }
        }
      }
      [pscustomobject]@{
        userNo = $UserNo
        success = $claim.success
        code = $claim.code
        status = $claim.data.status
      }
    } catch {
      [pscustomobject]@{
        userNo = $UserNo
        success = $false
        code = "EXCEPTION"
        status = $_.Exception.Message
      }
    }
  }
}

Wait-Job $jobs | Out-Null
$results = $jobs | Receive-Job
$jobs | Remove-Job

$summary = $results | Group-Object status | Sort-Object Name | ForEach-Object {
  [pscustomobject]@{
    status = $_.Name
    count = $_.Count
  }
}

Write-Host "[mealflow-load] seckill users=$Users voucherId=$VoucherId"
$summary | Format-Table -AutoSize

if (($results | Where-Object { -not $_.success -and $_.code -eq "EXCEPTION" }).Count -gt 0) {
  throw "Seckill load test has request exceptions"
}

if ($ExpectedClaimed -ge 0) {
  $claimed = @($results | Where-Object { $_.status -eq "CLAIMED" }).Count
  if ($claimed -ne $ExpectedClaimed) {
    throw "Expected $ExpectedClaimed claimed vouchers, got $claimed"
  }
}
