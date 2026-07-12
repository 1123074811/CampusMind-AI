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
    @{Name="campus-gateway";         Port=8080}
    @{Name="campus-auth-service";    Port=8081}
    @{Name="campus-event-service";   Port=8083}
    @{Name="campus-feed-service";    Port=8084}
    @{Name="campus-import-service";  Port=8085}
    @{Name="campus-crawler-service"; Port=8086}
    @{Name="campus-ai-service";      Port=8089}
)

foreach ($svc in $services) {
    $jar = "$ProjectRoot\$($svc.Name)\target\$($svc.Name)-0.1.0-SNAPSHOT.jar"
    $log = "$LogDir\$($svc.Name).log"
    $errLog = "$LogDir\$($svc.Name)-err.log"
    Start-Process -FilePath $Java -ArgumentList "-jar", $jar, "--server.port=$($svc.Port)" `
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

# Switch AI to LLM mode
$aiBaseUrl = $env:AI_BASE_URL
$aiModel = $env:AI_MODEL
$aiApiKey = $env:AI_API_KEY
if ($aiBaseUrl -and $aiModel -and $aiApiKey) {
    try {
        $bodyJson = "{`"mode`":`"llm`",`"baseUrl`":`"$aiBaseUrl`",`"model`":`"$aiModel`",`"apiKey`":`"$aiApiKey`"}"
        $body = [System.Text.Encoding]::UTF8.GetBytes($bodyJson)
        $r = Invoke-WebRequest -Uri "http://localhost:8089/api/v1/ai/runtime-config" -Method PUT -Body $body -ContentType "application/json" -UseBasicParsing -TimeoutSec 10
        Write-Host "AI LLM mode enabled: $($r.StatusCode)"
    } catch {
        Write-Host "AI mode switch: $($_.Exception.Message)"
    }
} else {
    Write-Host "AI LLM mode skipped (AI_BASE_URL / AI_MODEL / AI_API_KEY not set in .env)"
}

Write-Host "Done."
