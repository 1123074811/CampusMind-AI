<#
.SYNOPSIS
    CampusMind AI 开发阶段启动脚本
.DESCRIPTION
    启动全部后端微服务 + 前端管理后台。服务以独立进程运行（Start-Process），
    脚本退出后服务保持。jar 已存在则跳过构建。日志输出到 logs/ 目录。
    停止服务用 dev-stop.ps1。
#>

$ErrorActionPreference = "Stop"
$ProjectRoot = $PSScriptRoot
Set-Location $ProjectRoot

$JavaHome = "C:\Program Files\Java\jdk-21"
$Java    = "$JavaHome\bin\java.exe"
$LogDir  = "$ProjectRoot\logs"

$BackendServices = @(
    @{Name="campus-gateway";         Port=8080; Profiles=""}
    @{Name="campus-auth-service";    Port=8081; Profiles=""}
    @{Name="campus-user-service";    Port=8082; Profiles=""}
    @{Name="campus-event-service";   Port=8083; Profiles=""}
    @{Name="campus-feed-service";    Port=8084; Profiles=""}
    @{Name="campus-import-service";  Port=8085; Profiles=""}
    @{Name="campus-crawler-service"; Port=8086; Profiles=""}
    @{Name="campus-audit-service";   Port=8087; Profiles=""}
    @{Name="campus-search-service";  Port=8088; Profiles=""}
    @{Name="campus-ai-service";      Port=8089; Profiles="llm,local"}
)
$FrontendPort = 5173

# ------------------------------------------------------------
# 预备检查
# ------------------------------------------------------------
if (-not (Test-Path $Java)) {
    Write-Host "[ERROR] JDK 21 not found: $JavaHome" -ForegroundColor Red
    exit 1
}
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

# ------------------------------------------------------------
# 0. 清理残留进程与端口
# ------------------------------------------------------------
Write-Host "[0/3] Cleaning up leftover processes..." -ForegroundColor Cyan
Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
foreach ($p in 8080..8089 + $FrontendPort) {
    $line = netstat -ano | Select-String ":$p .*LISTENING"
    if ($line) {
        $pidv = ($line -split '\s+')[-1]
        Stop-Process -Id $pidv -Force -ErrorAction SilentlyContinue
    }
}
Start-Sleep -Seconds 2
Remove-Item "$LogDir\*.log" -Force -ErrorAction SilentlyContinue
Write-Host "      Clean" -ForegroundColor Green

# ------------------------------------------------------------
# 1. 构建检查（jar 都存在则跳过）
# ------------------------------------------------------------
Write-Host "`n[1/3] Backend jars..." -ForegroundColor Cyan
$missing = @()
foreach ($svc in $BackendServices) {
    $jar = "$ProjectRoot\$($svc.Name)\target\$($svc.Name)-0.1.0-SNAPSHOT.jar"
    if (-not (Test-Path $jar)) { $missing += $svc.Name }
}
if ($missing.Count -eq 0) {
    Write-Host "      All jars exist, skip build" -ForegroundColor Green
} else {
    Write-Host "      Missing jars: $($missing -join ', '). Building..." -ForegroundColor Yellow
    $MvnLog = "$LogDir\maven-build.log"
    $build = Start-Process -FilePath "mvn" `
        -ArgumentList "clean","package","-DskipTests","-q" `
        -NoNewWindow -Wait -PassThru `
        -RedirectStandardOutput $MvnLog -RedirectStandardError "$LogDir\maven-error.log"
    if ($build.ExitCode -ne 0) {
        Write-Host "[ERROR] Maven build failed. Check logs/maven-build.log" -ForegroundColor Red
        Get-Content $MvnLog -Tail 20
        exit 1
    }
    Write-Host "      Backend build OK" -ForegroundColor Green
}

# ------------------------------------------------------------
# 2. 启动全部服务（独立进程）
# ------------------------------------------------------------
Write-Host "`n[2/3] Starting services..." -ForegroundColor Cyan
foreach ($svc in $BackendServices) {
    $jar = "$ProjectRoot\$($svc.Name)\target\$($svc.Name)-0.1.0-SNAPSHOT.jar"
    $log = "$LogDir\$($svc.Name).log"
    $errLog = "$LogDir\$($svc.Name)-err.log"
    $jargs = @("-jar", $jar)
    if ($svc.Profiles) { $jargs += "--spring.profiles.active=$($svc.Profiles)" }
    Start-Process -FilePath $Java -ArgumentList $jargs -WorkingDirectory $ProjectRoot `
        -WindowStyle Hidden -RedirectStandardOutput $log -RedirectStandardError $errLog
    Write-Host "      [BE]  $($svc.Name)  -> http://localhost:$($svc.Port)" -ForegroundColor Green
}

# 前端
$feLog = "$LogDir\campus-admin-web.log"
Start-Process -FilePath "cmd.exe" -ArgumentList "/c","npm run dev" `
    -WorkingDirectory "$ProjectRoot\campus-admin-web" `
    -WindowStyle Hidden -RedirectStandardOutput $feLog -RedirectStandardError "$LogDir\campus-admin-web-err.log"
Write-Host "      [FE]  campus-admin-web -> http://localhost:$FrontendPort" -ForegroundColor Green

# ------------------------------------------------------------
# 3. 健康检查
# ------------------------------------------------------------
Write-Host "`n[3/3] Waiting for services to boot..." -ForegroundColor Cyan
$targets = $BackendServices | ForEach-Object { @{Label=$_.Name; Port=$_.Port; Url="http://localhost:$($_.Port)/actuator/health"} }
$targets += @(@{Label="campus-admin-web"; Port=$FrontendPort; Url="http://localhost:$FrontendPort"})

$up = @{}
$timeout = 90
$elapsed = 0
while ($elapsed -lt $timeout -and $up.Count -lt $targets.Count) {
    foreach ($t in $targets) {
        if ($up.ContainsKey($t.Port)) { continue }
        try {
            $res = Invoke-WebRequest -Uri $t.Url -TimeoutSec 1 -UseBasicParsing -ErrorAction SilentlyContinue
            if ($res.StatusCode -eq 200) {
                $up[$t.Port] = $true
                Write-Host "      [UP]  $($t.Label) :$($t.Port)" -ForegroundColor Green
            }
        } catch {}
    }
    if ($up.Count -lt $targets.Count) { Start-Sleep -Seconds 3; $elapsed += 3 }
}

$down = $targets | Where-Object { -not $up.ContainsKey($_.Port) }
Write-Host "`n============================================" -ForegroundColor DarkCyan
Write-Host "  Started: $($up.Count)/$($targets.Count)" -ForegroundColor $(if($down.Count -eq 0){"Green"}else{"Yellow"})
Write-Host "  Gateway : http://localhost:8080" -ForegroundColor White
Write-Host "  Admin   : http://localhost:5173" -ForegroundColor White
Write-Host "  Logs    : $LogDir" -ForegroundColor White
if ($down.Count -gt 0) {
    Write-Host "  Down   : $($down.Label -join ', ') (check logs/)" -ForegroundColor Yellow
}
Write-Host "  Stop   : .\dev-stop.ps1" -ForegroundColor White
Write-Host "============================================`n" -ForegroundColor DarkCyan
