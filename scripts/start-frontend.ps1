param(
  [switch]$Stop,
  [switch]$NoInstall,
  [int]$AdminPort = 5173,
  [int]$UserPort = 5174
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$logDir = Join-Path $root "logs\frontend"
$pidDir = Join-Path $root ".frontend-pids"

$apps = @(
  @{
    Name = "admin"
    DisplayName = "MealFlow admin web"
    Directory = "meal-web"
    Port = $AdminPort
  },
  @{
    Name = "user"
    DisplayName = "MealFlow user H5"
    Directory = "meal-user-web"
    Port = $UserPort
  }
)

function Get-PidFile($app) {
  Join-Path $pidDir "$($app.Name).pid"
}

function Test-ProcessRunning($processId) {
  if ([string]::IsNullOrWhiteSpace($processId)) {
    return $false
  }
  return [bool](Get-Process -Id $processId -ErrorAction SilentlyContinue)
}

function Test-PortOpen($port) {
  $client = New-Object System.Net.Sockets.TcpClient
  try {
    $connect = $client.BeginConnect("127.0.0.1", $port, $null, $null)
    if (!$connect.AsyncWaitHandle.WaitOne(500, $false)) {
      return $false
    }
    $client.EndConnect($connect)
    return $true
  } catch {
    return $false
  } finally {
    $client.Close()
  }
}

function Stop-Frontend($app) {
  $pidFile = Get-PidFile $app
  if (!(Test-Path $pidFile)) {
    Write-Host "$($app.DisplayName) is not recorded as running."
    return
  }

  $processId = (Get-Content $pidFile -Raw).Trim()
  if (Test-ProcessRunning $processId) {
    taskkill.exe /PID $processId /T /F | Out-Null
    Write-Host "Stopped $($app.DisplayName)."
  } else {
    Write-Host "$($app.DisplayName) process is already gone."
  }
  Remove-Item $pidFile -Force
}

function Ensure-Dependencies($app) {
  $appDir = Join-Path $root $app.Directory
  $nodeModules = Join-Path $appDir "node_modules"
  if (Test-Path $nodeModules) {
    return
  }

  if ($NoInstall) {
    throw "Missing dependencies in $($app.Directory). Run npm.cmd --prefix $($app.Directory) install first."
  }

  Write-Host "Installing dependencies for $($app.DisplayName)..."
  npm.cmd --prefix $appDir install
}

function Start-Frontend($app) {
  $pidFile = Get-PidFile $app
  if (Test-Path $pidFile) {
    $existingPid = (Get-Content $pidFile -Raw).Trim()
    if (Test-ProcessRunning $existingPid) {
      Write-Host "$($app.DisplayName) is already running at http://localhost:$($app.Port)/"
      return
    }
    Remove-Item $pidFile -Force
  }

  if (Test-PortOpen $app.Port) {
    Write-Host "Port $($app.Port) is already in use. $($app.DisplayName) may already be running at http://localhost:$($app.Port)/"
    return
  }

  Ensure-Dependencies $app

  $appDir = Join-Path $root $app.Directory
  $outLog = Join-Path $logDir "$($app.Name).out.log"
  $errLog = Join-Path $logDir "$($app.Name).err.log"
  $command = "cd /d `"$appDir`" && npm.cmd run dev -- --host 0.0.0.0 --port $($app.Port) > `"$outLog`" 2> `"$errLog`""
  $process = Start-Process -FilePath "cmd.exe" -ArgumentList @("/c", $command) -WorkingDirectory $root -WindowStyle Hidden -PassThru
  Set-Content -Path $pidFile -Value $process.Id

  Write-Host "Started $($app.DisplayName): http://localhost:$($app.Port)/"
  Write-Host "  stdout: $outLog"
  Write-Host "  stderr: $errLog"
}

New-Item -ItemType Directory -Force -Path $logDir, $pidDir | Out-Null

if ($Stop) {
  foreach ($app in $apps) {
    Stop-Frontend $app
  }
  exit 0
}

foreach ($app in $apps) {
  Start-Frontend $app
}

Write-Host ""
Write-Host "Frontend services are starting."
Write-Host "Admin web: http://localhost:$AdminPort/"
Write-Host "User H5:    http://localhost:$UserPort/"
Write-Host "Stop them with: .\start-frontend.cmd -Stop"
Write-Host "Or: powershell.exe -ExecutionPolicy Bypass -File .\scripts\start-frontend.ps1 -Stop"
