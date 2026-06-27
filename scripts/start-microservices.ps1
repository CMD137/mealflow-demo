$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$modules = @(
  "meal-auth-user",
  "meal-merchant",
  "meal-catalog",
  "meal-cart",
  "meal-order",
  "meal-queue",
  "meal-promotion",
  "meal-payment",
  "meal-fulfillment",
  "meal-notify",
  "meal-gateway"
)

Push-Location $root
try {
  mvn -q -DskipTests package

  foreach ($module in $modules) {
    $jar = Join-Path $root "$module\target\$module-0.1.0-SNAPSHOT.jar"
    if (!(Test-Path $jar)) {
      throw "Jar not found: $jar"
    }
    Start-Process -FilePath "java" -ArgumentList @("-jar", $jar) -WorkingDirectory $root -WindowStyle Hidden
  }

  Write-Host "MealFlow microservices are starting."
  Write-Host "Gateway: http://localhost:8080/ping"
  Write-Host "Example routed ping: http://localhost:8080/orders/ping"
} finally {
  Pop-Location
}
