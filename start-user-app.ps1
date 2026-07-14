$ErrorActionPreference = 'Stop'

$appDir = Join-Path $PSScriptRoot 'campus-flutter-app'
$exe = Join-Path $appDir 'build\windows\x64\runner\Release\campus_flutter_app.exe'

Get-Process campus_flutter_app -ErrorAction SilentlyContinue |
  Stop-Process -Force -ErrorAction SilentlyContinue

Push-Location $appDir
try {
  flutter build windows --dart-define=CAMPUSMIND_API_BASE=http://localhost:8080
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
  Pop-Location
}

Start-Process -FilePath $exe -WorkingDirectory $appDir
Write-Host 'CampusMind user app started.' -ForegroundColor Green
