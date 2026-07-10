$ErrorActionPreference = 'Stop'

$appDir = Join-Path $PSScriptRoot 'campus-flutter-app'
$exe = Join-Path $appDir 'build\windows\x64\runner\Debug\campus_flutter_app.exe'

Push-Location $appDir
try {
  flutter build windows
  if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally {
  Pop-Location
}

Start-Process -FilePath $exe
Write-Host 'CampusMind 用户端已启动。' -ForegroundColor Green
