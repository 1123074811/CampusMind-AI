$ErrorActionPreference = "Stop"
$ProjectRoot = "e:\code\CampusMind-AI"
Set-Location $ProjectRoot

# Load .env
foreach ($line in Get-Content "$ProjectRoot\.env") {
    $trimmed = $line.Trim()
    if (-not $trimmed -or $trimmed.StartsWith("#") -or -not $trimmed.Contains("=")) { continue }
    $parts = $trimmed.Split("=", 2)
    $name = $parts[0].Trim()
    $value = $parts[1].Trim().Trim('"').Trim("'")
    if ($name -and [string]::IsNullOrEmpty([Environment]::GetEnvironmentVariable($name))) {
        Set-Item -Path "Env:$name" -Value $value
    }
}

# Set DB credentials for all services
$LocalDbUsername = $env:MYSQL_USERNAME
if (-not $LocalDbUsername) { $LocalDbUsername = "campusmind" }
$LocalDbPassword = $env:MYSQL_PASSWORD
if (-not $LocalDbPassword) { $LocalDbPassword = "campusmind" }

foreach ($prefix in @("AUTH", "USER", "EVENT", "FEED", "IMPORT", "CRAWLER", "AUDIT", "SEARCH")) {
    Set-Item -Path "Env:${prefix}_DB_USERNAME" -Value $LocalDbUsername
    Set-Item -Path "Env:${prefix}_DB_PASSWORD" -Value $LocalDbPassword
}
if (-not $env:IMPORT_REDIS_PASSWORD) { $env:IMPORT_REDIS_PASSWORD = "" }

$Java = "C:\Program Files\Java\jdk-21\bin\java.exe"
$LogDir = "$ProjectRoot\logs"

# Kill old java processes
Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2

$services = @(
    @{Name="campus-gateway";         Port=8080; Profiles=""}
    @{Name="campus-auth-service";    Port=8081; Profiles=""}
    @{Name="campus-event-service";   Port=8083; Profiles=""}
    @{Name="campus-feed-service";    Port=8084; Profiles=""}
    @{Name="campus-import-service";  Port=8085; Profiles=""}
    @{Name="campus-crawler-service"; Port=8086; Profiles=""}
    @{Name="campus-ai-service";      Port=8089; Profiles=$(if ($env:CAMPUS_AI_MODE -eq "llm") { "llm,pg" } else { "" })}
)

foreach ($svc in $services) {
    $jar = "$ProjectRoot\$($svc.Name)\target\$($svc.Name)-0.1.0-SNAPSHOT.jar"
    $log = "$LogDir\$($svc.Name).log"
    $errLog = "$LogDir\$($svc.Name)-err.log"
    $jargs = @("-jar", $jar, "--server.port=$($svc.Port)")
    if ($svc.Profiles) { $jargs += "--spring.profiles.active=$($svc.Profiles)" }
    Start-Process -FilePath $Java -ArgumentList $jargs `
        -WorkingDirectory $ProjectRoot -RedirectStandardOutput $log -RedirectStandardError $errLog
    Write-Host "Started $($svc.Name) on port $($svc.Port)"
}

Write-Host "Waiting for services to boot..."
Start-Sleep -Seconds 35

foreach ($svc in $services) {
    $port = $svc.Port
    $conn = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue | Where-Object { $_.State -eq "Listen" }
    if ($conn) {
        Write-Host "  [OK]   $($svc.Name) port $port"
    } else {
        Write-Host "  [DOWN] $($svc.Name) port $port"
    }
}

Write-Host "Done."
