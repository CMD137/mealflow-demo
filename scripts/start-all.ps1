param(
  [switch]$SkipPackage,
  [switch]$Stop
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$frontendScript = Join-Path $PSScriptRoot "start-frontend.ps1"

Push-Location $root
try {
  if ($Stop) {
    Write-Host "Stopping MealFlow frontend services..."
    & $frontendScript -Stop

    Write-Host "Stopping MealFlow Docker services..."
    docker compose stop
    Write-Host "MealFlow services stopped."
    exit 0
  }

  if (!$SkipPackage) {
    Write-Host "Packaging backend services..."
    mvn -q -DskipTests package
  } else {
    Write-Host "Skipping backend package step."
  }

  Write-Host "Starting backend Docker services..."
  docker compose up -d

  Write-Host "Starting frontend services..."
  & $frontendScript

  Write-Host ""
  Write-Host "MealFlow is starting."
  Write-Host "Gateway:    http://localhost:8080/ping"
  Write-Host "Admin web:  http://localhost:5173/"
  Write-Host "User H5:    http://localhost:5174/"
  Write-Host "Prometheus: http://localhost:9090/"
  Write-Host "Grafana:    http://localhost:3000/  admin / mealflow"
  Write-Host ""
  Write-Host "Stop all services with: .\start-all.cmd -Stop"
  Write-Host "Skip backend package with: .\start-all.cmd -SkipPackage"
} finally {
  Pop-Location
}
