<#
.SYNOPSIS
    CampusMind AI 开发阶段一键启动脚本
.DESCRIPTION
    构建并启动全部后端微服务 + 前端管理后台，只有一个终端窗口。
    日志输出到 logs/ 目录，按 Ctrl+C 停止全部服务。
#>

$ErrorActionPreference = "Stop"
$ProjectRoot = $PSScriptRoot
Set-Location $ProjectRoot

$JavaHome = "C:\Program Files\Java\jdk-21"
$Java    = "$JavaHome\bin\java.exe"
$LogDir  = "$ProjectRoot\logs"
$MvnLog  = "$LogDir\maven-build.log"

$BackendPorts = 8080..8089
$FrontendPort = 5173

# ------------------------------------------------------------
# 预备检查
# ------------------------------------------------------------
if (-not (Test-Path $Java)) {
    Write-Host "[ERROR] JDK 21 not found: $JavaHome" -ForegroundColor Red
    exit 1
}
if (-not (netstat -ano | Select-String ":3306 .*LISTENING")) {
    Write-Host "[WARN] MySQL (3306) not listening — DB-configured services will fail" -ForegroundColor Yellow
}
if (-not (Test-Path "$ProjectRoot\campus-admin-web\node_modules")) {
    Write-Host "[WARN] campus-admin-web node_modules not found, will run npm install" -ForegroundColor Yellow
}

New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
Remove-Item "$LogDir\*.log" -Force -ErrorAction SilentlyContinue

# ------------------------------------------------------------
# 0. 清理残留进程（避免文件锁）
# ------------------------------------------------------------
Write-Host "[0/4] Cleaning up leftover processes..." -ForegroundColor Cyan
Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force
foreach ($p in $BackendPorts + $FrontendPort) {
    $line = netstat -ano | Select-String ":$p .*LISTENING"
    if ($line) {
        $pid = ($line -split '\s+')[-1]
        Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue
    }
}
Start-Sleep -Seconds 2
Write-Host "       Clean" -ForegroundColor Green

# ------------------------------------------------------------
# 1. 后端构建
# ------------------------------------------------------------
Write-Host "`n[1/4] Building backend (Maven)..." -ForegroundColor Cyan
$build = Start-Process -FilePath "mvn" `
    -ArgumentList "clean","package","-DskipTests","-q" `
    -NoNewWindow -Wait -PassThru `
    -RedirectStandardOutput $MvnLog -RedirectStandardError "$LogDir\maven-error.log"
if ($build.ExitCode -ne 0) {
    Write-Host "[ERROR] Maven build failed. Check logs/maven-build.log" -ForegroundColor Red
    Get-Content $MvnLog -Tail 20
    exit 1
}
Write-Host "       Backend build OK" -ForegroundColor Green

# ------------------------------------------------------------
# 2. 前端安装依赖（已有 node_modules 则跳过）
# ------------------------------------------------------------
Write-Host "`n[2/4] Frontend dependencies..." -ForegroundColor Cyan
if (Test-Path "$ProjectRoot\campus-admin-web\node_modules") {
    Write-Host "       node_modules exists, skip npm install" -ForegroundColor Green
} else {
    $npmCmd = (Get-Command npm.cmd -ErrorAction SilentlyContinue).Source
    if (-not $npmCmd) { $npmCmd = (Get-Command npm -ErrorAction SilentlyContinue).Source }
    if (-not $npmCmd) {
        Write-Host "[WARN] npm not found, skipping frontend dependency install" -ForegroundColor Yellow
    } else {
        Push-Location "$ProjectRoot\campus-admin-web"
        $npmInstall = Start-Process -FilePath $npmCmd -ArgumentList "install" `
            -NoNewWindow -Wait -PassThru `
            -RedirectStandardOutput "$LogDir\npm-install.log" -RedirectStandardError "$LogDir\npm-error.log"
        Pop-Location
        if ($npmInstall.ExitCode -eq 0) {
            Write-Host "       npm install OK" -ForegroundColor Green
        } else {
            Write-Host "[WARN] npm install failed (exit $($npmInstall.ExitCode)), continuing" -ForegroundColor Yellow
        }
    }
}

# ------------------------------------------------------------
# 3. 停止旧端口占用
# ------------------------------------------------------------
Write-Host "`n[3/4] Freeing ports ${BackendPorts[0]}-${BackendPorts[-1]}, $FrontendPort..." -ForegroundColor Cyan
foreach ($p in $BackendPorts + $FrontendPort) {
    $line = netstat -ano | Select-String ":$p .*LISTENING"
    if ($line) {
        $pid = ($line -split '\s+')[-1]
        Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue
        Write-Host "       Killed PID $pid on port $p"
    }
}
Start-Sleep -Seconds 1

# ------------------------------------------------------------
# 4. 启动全部服务
# ------------------------------------------------------------
Write-Host "`n[4/4] Starting all services..." -ForegroundColor Cyan

# --- 后端微服务 ---
$BackendServices = @(
    @{Name="campus-gateway";         Port=8080; Jar="campus-gateway\target\campus-gateway-0.1.0-SNAPSHOT.jar"}
    @{Name="campus-auth-service";    Port=8081; Jar="campus-auth-service\target\campus-auth-service-0.1.0-SNAPSHOT.jar"}
    @{Name="campus-user-service";    Port=8082; Jar="campus-user-service\target\campus-user-service-0.1.0-SNAPSHOT.jar"}
    @{Name="campus-event-service";   Port=8083; Jar="campus-event-service\target\campus-event-service-0.1.0-SNAPSHOT.jar"}
    @{Name="campus-feed-service";    Port=8084; Jar="campus-feed-service\target\campus-feed-service-0.1.0-SNAPSHOT.jar"}
    @{Name="campus-import-service";  Port=8085; Jar="campus-import-service\target\campus-import-service-0.1.0-SNAPSHOT.jar"}
    @{Name="campus-crawler-service"; Port=8086; Jar="campus-crawler-service\target\campus-crawler-service-0.1.0-SNAPSHOT.jar"}
    @{Name="campus-audit-service";   Port=8087; Jar="campus-audit-service\target\campus-audit-service-0.1.0-SNAPSHOT.jar"}
    @{Name="campus-search-service";  Port=8088; Jar="campus-search-service\target\campus-search-service-0.1.0-SNAPSHOT.jar"}
    @{Name="campus-ai-service";      Port=8089; Jar="campus-ai-service\target\campus-ai-service-0.1.0-SNAPSHOT.jar"}
)

foreach ($svc in $BackendServices) {
    $jarPath = Join-Path $ProjectRoot $svc.Jar
    if (-not (Test-Path $jarPath)) {
        Write-Host "       [SKIP] $($svc.Name) — JAR not found" -ForegroundColor Yellow
        continue
    }
    $logFile = "$LogDir\$($svc.Name).log"
    Start-Job -Name $svc.Name -ArgumentList $Java, $jarPath, $logFile {
        param($javaBin, $jar, $log)
        & $javaBin -jar $jar *>&1 >> $log
    } | Out-Null
    Write-Host "       [BE]  $($svc.Name)  -> http://localhost:$($svc.Port)" -ForegroundColor Green
}

# --- 前端管理后台 ---
$feLog = "$LogDir\campus-admin-web.log"
Start-Job -Name "campus-admin-web" -ArgumentList $ProjectRoot, $feLog {
    param($root, $log)
    Set-Location "$root\campus-admin-web"
    & npm run dev *>&1 >> $log
} | Out-Null
Write-Host "       [FE]  campus-admin-web -> http://localhost:$FrontendPort" -ForegroundColor Green

# ------------------------------------------------------------
# 健康检查
# ------------------------------------------------------------
Write-Host "`nWaiting for services to boot..." -ForegroundColor Cyan
$allTargets = ($BackendServices | ForEach-Object { @{Label=$_.Name; Port=$_.Port; Url="http://localhost:$($_.Port)/actuator/health"} }) +
              @(@{Label="campus-admin-web"; Port=5173; Url="http://localhost:5173"})

$up = @{}
$timeout = 120
$elapsed = 0
while ($elapsed -lt $timeout -and $up.Count -lt $allTargets.Count) {
    foreach ($t in $allTargets) {
        if ($up.ContainsKey($t.Port)) { continue }
        try {
            $res = Invoke-WebRequest -Uri $t.Url -TimeoutSec 1 -UseBasicParsing -ErrorAction SilentlyContinue
            if ($res.StatusCode -eq 200) {
                $up[$t.Port] = $true
                Write-Host "       [UP]  $($t.Label) :$($t.Port)" -ForegroundColor Green
            }
        } catch {}
    }
    Start-Sleep -Seconds 2
    $elapsed += 2
}
if ($up.Count -lt $allTargets.Count) {
    Write-Host "`n       Some services still starting — check logs/ for details" -ForegroundColor Yellow
}

# ------------------------------------------------------------
# 运行中
# ------------------------------------------------------------
Write-Host "`n============================================" -ForegroundColor DarkCyan
Write-Host "  All services launched. Press Ctrl+C to stop." -ForegroundColor White
Write-Host "  Gateway : http://localhost:8080" -ForegroundColor White
Write-Host "  Admin   : http://localhost:5173" -ForegroundColor White
Write-Host "  Logs    : $LogDir" -ForegroundColor White
Write-Host "============================================`n" -ForegroundColor DarkCyan

netstat -ano | Select-String "LISTENING" | Select-String "808[0-9]|5173"

# 保持运行，Ctrl+C 时清理
$null = Register-EngineEvent -SourceIdentifier PowerShell.Exiting -Action {
    Get-Job | Stop-Job
    Get-Job | Remove-Job -Force
    Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force
    Write-Host "`nAll services stopped."
}
try {
    while ($true) { Start-Sleep -Seconds 5 }
} finally {
    Get-Job | Stop-Job
    Get-Job | Remove-Job -Force
    Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force
    Write-Host "`nAll services stopped."
}
