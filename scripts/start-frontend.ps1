param(
  [switch]$Stop
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
    Port = 5173
  },
  @{
    Name = "user"
    DisplayName = "MealFlow user H5"
    Directory = "meal-user-web"
    Port = 5174
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

function Stop-PortListener($app) {
  $listeners = Get-NetTCPConnection -LocalPort $app.Port -State Listen -ErrorAction SilentlyContinue |
      Select-Object -ExpandProperty OwningProcess -Unique

  foreach ($processId in $listeners) {
    if ($processId -le 0) {
      continue
    }
    if (Test-ProcessRunning $processId) {
      taskkill.exe /PID $processId /T /F | Out-Null
      Write-Host "Stopped process $processId on port $($app.Port) for $($app.DisplayName)."
    }
  }
}

function Ensure-Dependencies($app) {
  $appDir = Join-Path $root $app.Directory
  $nodeModules = Join-Path $appDir "node_modules"
  if (Test-Path $nodeModules) {
    return
  }

  Write-Host "Installing dependencies for $($app.DisplayName)..."
  npm.cmd --prefix $appDir install
}

function Reset-ViteCache($app) {
  $appDir = Join-Path $root $app.Directory
  $cacheDir = Join-Path $appDir "node_modules\.vite"
  if (!(Test-Path $cacheDir)) {
    return
  }

  $resolvedRoot = [System.IO.Path]::GetFullPath($root)
  $resolvedCacheDir = [System.IO.Path]::GetFullPath($cacheDir)
  if (!$resolvedCacheDir.StartsWith($resolvedRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing to remove Vite cache outside workspace: $resolvedCacheDir"
  }

  Remove-Item -LiteralPath $resolvedCacheDir -Recurse -Force
}

function Start-Frontend($app) {
  $pidFile = Get-PidFile $app

  Stop-Frontend $app
  Stop-PortListener $app

  Ensure-Dependencies $app
  Reset-ViteCache $app

  $appDir = Join-Path $root $app.Directory
  $outLog = Join-Path $logDir "$($app.Name).out.log"
  $errLog = Join-Path $logDir "$($app.Name).err.log"
  $command = "cd /d `"$appDir`" && npm.cmd run dev -- --host 0.0.0.0 --port $($app.Port) --force > `"$outLog`" 2> `"$errLog`""
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
    Stop-PortListener $app
  }
  exit 0
}

foreach ($app in $apps) {
  Start-Frontend $app
}

Write-Host ""
Write-Host "Frontend services are starting."
Write-Host "Admin web: http://localhost:5173/"
Write-Host "User H5:    http://localhost:5174/"
Write-Host "Stop them with: .\start-frontend.cmd -Stop"
Write-Host "Or: powershell.exe -ExecutionPolicy Bypass -File .\scripts\start-frontend.ps1 -Stop"
