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

function Import-DotEnv {
    param([string]$Path)
    if (-not (Test-Path $Path)) { return }
    foreach ($line in Get-Content $Path) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#") -or -not $trimmed.Contains("=")) { continue }
        $parts = $trimmed.Split("=", 2)
        $name = $parts[0].Trim()
        $value = $parts[1].Trim().Trim('"').Trim("'")
        if ($name -and [string]::IsNullOrEmpty([Environment]::GetEnvironmentVariable($name))) {
            Set-Item -Path "Env:$name" -Value $value
        }
    }
}

Import-DotEnv "$ProjectRoot\.env"

$JavaHome = "C:\Program Files\Java\jdk-21"
$Java    = "$JavaHome\bin\java.exe"
$LogDir  = "$ProjectRoot\logs"
$InfraComposeFile = "$ProjectRoot\infra\docker-compose.yml"

if (-not $env:MYSQL_HOST) { $env:MYSQL_HOST = "localhost" }
if (-not $env:MYSQL_PORT) { $env:MYSQL_PORT = "3306" }
if (-not $env:MYSQL_DATABASE) { $env:MYSQL_DATABASE = "campusmind" }
if (-not $env:REDIS_HOST) { $env:REDIS_HOST = "localhost" }
if (-not $env:REDIS_PORT) { $env:REDIS_PORT = "6379" }
if (-not $env:MONGODB_URI) { $env:MONGODB_URI = "mongodb://localhost:27017/campusmind" }
$MongoUri = [Uri]$env:MONGODB_URI
$MongoHost = if ($MongoUri.Host) { $MongoUri.Host } else { "localhost" }
$MongoPort = if ($MongoUri.Port -gt 0) { $MongoUri.Port } else { 27017 }

$LocalDbUsername = $env:MYSQL_USERNAME
if (-not $LocalDbUsername) { $LocalDbUsername = "campusmind" }
$LocalDbPassword = $env:MYSQL_PASSWORD
if (-not $LocalDbPassword) { $LocalDbPassword = "campusmind" }

foreach ($prefix in @("AUTH", "USER", "EVENT", "FEED", "IMPORT", "CRAWLER", "AUDIT", "SEARCH")) {
    Set-Item -Path "Env:${prefix}_DB_USERNAME" -Value $LocalDbUsername
    Set-Item -Path "Env:${prefix}_DB_PASSWORD" -Value $LocalDbPassword
}
if (-not $env:IMPORT_REDIS_PASSWORD) { $env:IMPORT_REDIS_PASSWORD = "" }

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
    @{Name="campus-ai-service";      Port=8089; Profiles=""}
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

function Test-TcpPort {
    param(
        [string]$HostName,
        [int]$Port
    )
    try {
        $client = [System.Net.Sockets.TcpClient]::new()
        $client.Connect($HostName, $Port)
        $client.Dispose()
        Write-Host "      [TCP OK] ${HostName}:${Port}" -ForegroundColor DarkGray
        return $true
    } catch {
        Write-Host "      [TCP FAIL] ${HostName}:${Port} — $($_.Exception.Message)" -ForegroundColor DarkGray
        return $false
    }
}

function Get-DockerComposeCommand {
    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $docker) { return $null }
    return @("docker", "compose")
}

function Start-NativeServiceIfPresent {
    param(
        [string[]]$Names
    )
    foreach ($name in $Names) {
        $svc = Get-Service -Name $name -ErrorAction SilentlyContinue
        if (-not $svc) { continue }
        if ($svc.Status -ne "Running") {
            Write-Host "      Native service found: $name. Starting..." -ForegroundColor Yellow
            try {
                Start-Service -Name $name -ErrorAction Stop
            } catch {
                Write-Host "      Native service '$name' could not be started: $($_.Exception.Message)" -ForegroundColor Yellow
                continue
            }
        }
        return $true
    }
    return $false
}

function Ensure-InfraService {
    param(
        [string]$Label,
        [string]$HostName,
        [int]$Port,
        [string]$ComposeService,
        [string]$ContainerName,
        [string[]]$NativeServiceNames
    )

    if (Test-TcpPort -HostName $HostName -Port $Port) {
        Write-Host "      [OK]  $Label is reachable at ${HostName}:${Port}" -ForegroundColor Green
        return
    }

    if (Start-NativeServiceIfPresent -Names $NativeServiceNames) {
        Start-Sleep -Seconds 3
        if (Test-TcpPort -HostName $HostName -Port $Port) {
            Write-Host "      [OK]  $Label native service started" -ForegroundColor Green
            return
        }
    }

    $compose = Get-DockerComposeCommand
    if (-not $compose) {
        Write-Host "[ERROR] $Label is not running, and Docker was not found for first deployment install." -ForegroundColor Red
        Write-Host "        Please install Docker Desktop or install/start $Label manually, then rerun this script." -ForegroundColor Red
        exit 1
    }
    if (-not (Test-Path $InfraComposeFile)) {
        Write-Host "[ERROR] Docker compose file not found: $InfraComposeFile" -ForegroundColor Red
        exit 1
    }

    $existingContainer = docker ps -a --filter "name=^/$ContainerName$" --format "{{.Names}}" 2>$null
    if ($existingContainer -eq $ContainerName) {
        Write-Host "      Container found: $ContainerName. Starting..." -ForegroundColor Yellow
        docker start $ContainerName | Out-Null
    } else {
        Write-Host "      $Label not found. Installing via Docker Compose service '$ComposeService'..." -ForegroundColor Yellow
        $composeExe = $compose[0]
        $composeArgs = @($compose[1], "-f", $InfraComposeFile, "up", "-d", $ComposeService)
        $restoreEnv = @{}
        if ($ComposeService -eq "nacos") {
            foreach ($name in @("MYSQL_ROOT_PASSWORD", "MYSQL_PASSWORD", "PGVECTOR_PASSWORD")) {
                $restoreEnv[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
                if ([string]::IsNullOrEmpty($restoreEnv[$name])) {
                    Set-Item "Env:$name" "compose-placeholder-not-persisted"
                }
            }
        }
        try {
            & $composeExe @composeArgs
        } finally {
            foreach ($name in $restoreEnv.Keys) {
                if ($null -eq $restoreEnv[$name]) {
                    Remove-Item "Env:$name" -ErrorAction SilentlyContinue
                } else {
                    Set-Item "Env:$name" $restoreEnv[$name]
                }
            }
        }
        if ($LASTEXITCODE -ne 0) {
            Write-Host "[ERROR] Failed to install/start $Label with Docker Compose." -ForegroundColor Red
            exit 1
        }
    }

    $waited = 0
    while ($waited -lt 45) {
        if (Test-TcpPort -HostName $HostName -Port $Port) {
            Write-Host "      [OK]  $Label is running at ${HostName}:${Port}" -ForegroundColor Green
            return
        }
        Start-Sleep -Seconds 3
        $waited += 3
    }
    Write-Host "[ERROR] $Label did not become reachable at ${HostName}:${Port}" -ForegroundColor Red
    exit 1
}

function Invoke-MySqlScript {
    param(
        [string]$ScriptPath,
        [string]$Username = $LocalDbUsername,
        [string]$Password = $LocalDbPassword,
        [string]$Database = $env:MYSQL_DATABASE
    )
    if (-not (Test-Path $ScriptPath)) {
        Write-Host "[ERROR] MySQL script not found: $ScriptPath" -ForegroundColor Red
        exit 1
    }

    $mysql = Get-Command mysql -ErrorAction SilentlyContinue
    $previousMysqlPwd = $env:MYSQL_PWD
    $env:MYSQL_PWD = $Password
    try {
        if ($mysql) {
            $sourcePath = $ScriptPath.Replace("\", "/")
            $output = & $mysql.Source `
                "--host=$($env:MYSQL_HOST)" `
                "--port=$($env:MYSQL_PORT)" `
                "--user=$Username" `
                "--database=$Database" `
                "--default-character-set=utf8mb4" `
                "--execute=source $sourcePath"
            $exitCode = $LASTEXITCODE
            $output | Write-Host
            return $exitCode
        }

        $compose = Get-DockerComposeCommand
        if ($compose) {
            $container = docker ps --filter "name=^/campusmind-mysql$" --format "{{.Names}}" 2>$null
            if ($container -eq "campusmind-mysql") {
                Get-Content -Encoding UTF8 $ScriptPath | docker exec -i campusmind-mysql mysql `
                    "--user=$Username" `
                    "--database=$Database" `
                    "--default-character-set=utf8mb4"
                return $LASTEXITCODE
            }
        }
    } finally {
        $env:MYSQL_PWD = $previousMysqlPwd
    }

    Write-Host "[ERROR] mysql client not found and Docker MySQL container is not available." -ForegroundColor Red
    return 1
}

function Invoke-MySqlCommand {
    param(
        [string]$Sql,
        [string]$Username,
        [string]$Password,
        [string]$Database = ""
    )
    $mysql = Get-Command mysql -ErrorAction SilentlyContinue
    if (-not $mysql) { return 1 }
    $previousMysqlPwd = $env:MYSQL_PWD
    $env:MYSQL_PWD = $Password
    try {
        $args = @(
            "--host=$($env:MYSQL_HOST)",
            "--port=$($env:MYSQL_PORT)",
            "--user=$Username",
            "--default-character-set=utf8mb4",
            "--execute=$Sql"
        )
        if ($Database) { $args += "--database=$Database" }
        & $mysql.Source @args | Out-Null
        return $LASTEXITCODE
    } finally {
        $env:MYSQL_PWD = $previousMysqlPwd
    }
}

function Get-MySqlScalar {
    param(
        [string]$Sql,
        [string]$Username = $LocalDbUsername,
        [string]$Password = $LocalDbPassword,
        [string]$Database = $env:MYSQL_DATABASE
    )
    $mysql = Get-Command mysql -ErrorAction SilentlyContinue
    if (-not $mysql) { return $null }
    $previousMysqlPwd = $env:MYSQL_PWD
    $env:MYSQL_PWD = $Password
    try {
        $args = @(
            "--host=$($env:MYSQL_HOST)",
            "--port=$($env:MYSQL_PORT)",
            "--user=$Username",
            "--default-character-set=utf8mb4",
            "--skip-column-names",
            "--execute=$Sql"
        )
        if ($Database) { $args += "--database=$Database" }
        return (& $mysql.Source @args | Select-Object -First 1).ToString().Trim()
    } finally {
        $env:MYSQL_PWD = $previousMysqlPwd
    }
}

function Ensure-MySqlSchema {
    $testExit = Invoke-MySqlCommand `
        -Sql "SELECT 1" `
        -Username $LocalDbUsername `
        -Password $LocalDbPassword `
        -Database $env:MYSQL_DATABASE
    if ($testExit -ne 0) {
        if (-not $env:MYSQL_ROOT_PASSWORD) {
            Write-Host "[ERROR] MySQL is reachable, but user '$LocalDbUsername' cannot access database '$($env:MYSQL_DATABASE)'." -ForegroundColor Red
            Write-Host "        Create .env with MYSQL_USERNAME, MYSQL_PASSWORD and MYSQL_ROOT_PASSWORD, then rerun this script." -ForegroundColor Red
            Write-Host "        The script will use MYSQL_ROOT_PASSWORD only to initialize the database/user on first deployment." -ForegroundColor Red
            exit 1
        }

        Write-Host "      App MySQL user is not ready. Initializing database/user with MYSQL_ROOT_PASSWORD..." -ForegroundColor Yellow
        $escapedPassword = $LocalDbPassword.Replace("'", "''")
        $bootstrapSql = @"
CREATE DATABASE IF NOT EXISTS $($env:MYSQL_DATABASE) DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '$LocalDbUsername'@'%' IDENTIFIED BY '$escapedPassword';
CREATE USER IF NOT EXISTS '$LocalDbUsername'@'localhost' IDENTIFIED BY '$escapedPassword';
GRANT ALL PRIVILEGES ON $($env:MYSQL_DATABASE).* TO '$LocalDbUsername'@'%';
GRANT ALL PRIVILEGES ON $($env:MYSQL_DATABASE).* TO '$LocalDbUsername'@'localhost';
FLUSH PRIVILEGES;
"@
        $bootstrapExit = Invoke-MySqlCommand `
            -Sql $bootstrapSql `
            -Username "root" `
            -Password $env:MYSQL_ROOT_PASSWORD
        if ($bootstrapExit -ne 0) {
            Write-Host "[ERROR] MySQL bootstrap failed. Please check MYSQL_ROOT_PASSWORD in .env." -ForegroundColor Red
            exit 1
        }
    }

    $schemaScripts = @(
        "$ProjectRoot\infra\mysql\init\001_schema.sql",
        "$ProjectRoot\infra\mysql\init\004_web_crawl_item.sql",
        "$ProjectRoot\infra\mysql\init\005_web_crawl_item_detail.sql",
        "$ProjectRoot\infra\mysql\init\006_information_item.sql",
        "$ProjectRoot\infra\mysql\init\007_information_ai_card.sql",
        "$ProjectRoot\infra\mysql\init\007_user_subscription.sql"
    )
    foreach ($script in $schemaScripts) {
        if ((Split-Path $script -Leaf) -eq "007_information_ai_card.sql") {
            $aiColumnExists = Get-MySqlScalar `
                -Sql "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='$($env:MYSQL_DATABASE)' AND table_name='information_item' AND column_name='ai_status'" `
                -Username $LocalDbUsername `
                -Password $LocalDbPassword `
                -Database $env:MYSQL_DATABASE
            if ($aiColumnExists -ne 0) {
                Write-Host "      $(Split-Path $script -Leaf) already applied, skip" -ForegroundColor DarkGray
                continue
            }
        }
        Write-Host "      Applying $(Split-Path $script -Leaf)..." -ForegroundColor Yellow
        $exitCode = Invoke-MySqlScript -ScriptPath $script
        if ($exitCode -ne 0) {
            Write-Host "[ERROR] Failed to apply MySQL script: $script" -ForegroundColor Red
            exit 1
        }
    }
    $legacyInformationState = Get-MySqlScalar `
        -Sql "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='$($env:MYSQL_DATABASE)' AND table_name='user_information_state' AND column_name='read_status'" `
        -Username $LocalDbUsername `
        -Password $LocalDbPassword `
        -Database $env:MYSQL_DATABASE
    if ($legacyInformationState -gt 0) {
        Write-Host "      Applying enterprise persistence migration..." -ForegroundColor Yellow
        $exitCode = Invoke-MySqlScript -ScriptPath "$ProjectRoot\infra\mysql\migrations\002_enterprise_persistence.sql"
        if ($exitCode -ne 0) {
            Write-Host "[ERROR] Failed to migrate user persistence schema" -ForegroundColor Red
            exit 1
        }
    }
    # 003: schema integrity fixes (indexes, comments, redundant columns, FK rules)
    Write-Host "      Applying schema integrity fixes migration..." -ForegroundColor Yellow
    $exitCode = Invoke-MySqlScript -ScriptPath "$ProjectRoot\infra\mysql\migrations\003_schema_integrity_fixes.sql"
    if ($exitCode -ne 0) {
        Write-Host "[ERROR] Failed to apply schema integrity fixes" -ForegroundColor Red
        exit 1
    }
    foreach ($script in @(
        "$ProjectRoot\infra\mysql\init\002_admin_seed.sql",
        "$ProjectRoot\infra\mysql\init\003_public_sources.sql",
        "$ProjectRoot\infra\mysql\init\008_enterprise_constraints.sql"
    )) {
        Write-Host "      Applying $(Split-Path $script -Leaf)..." -ForegroundColor Yellow
        $exitCode = Invoke-MySqlScript -ScriptPath $script
        if ($exitCode -ne 0) {
            Write-Host "[ERROR] Failed to apply MySQL script: $script" -ForegroundColor Red
            exit 1
        }
    }
    Write-Host "      [OK]  MySQL schema ready" -ForegroundColor Green
}

# ------------------------------------------------------------
# 0. 基础设施自检（Redis / MongoDB）
# ------------------------------------------------------------
Write-Host "[0/4] Checking infrastructure..." -ForegroundColor Cyan
Ensure-InfraService `
    -Label "MySQL" `
    -HostName $env:MYSQL_HOST `
    -Port ([int]$env:MYSQL_PORT) `
    -ComposeService "mysql" `
    -ContainerName "campusmind-mysql" `
    -NativeServiceNames @("MySQL91", "MySQL90", "MySQL84", "MySQL80", "MySQL")
Ensure-InfraService `
    -Label "Redis" `
    -HostName $env:REDIS_HOST `
    -Port ([int]$env:REDIS_PORT) `
    -ComposeService "redis" `
    -ContainerName "campusmind-redis" `
    -NativeServiceNames @("Redis", "Redis Server", "redis")
Ensure-InfraService `
    -Label "MongoDB" `
    -HostName $MongoHost `
    -Port $MongoPort `
    -ComposeService "mongo" `
    -ContainerName "campusmind-mongo" `
    -NativeServiceNames @("MongoDB", "MongoDB Server", "mongodb")
Ensure-InfraService `
    -Label "Nacos" `
    -HostName "localhost" `
    -Port 8848 `
    -ComposeService "nacos" `
    -ContainerName "campusmind-nacos" `
    -NativeServiceNames @()

Ensure-MySqlSchema

# ------------------------------------------------------------
# 1. 清理残留进程与端口
# ------------------------------------------------------------
Write-Host "`n[1/4] Cleaning up leftover processes..." -ForegroundColor Cyan
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
# 2. 构建检查（jar 都存在则跳过）
# ------------------------------------------------------------
Write-Host "`n[2/4] Backend jars..." -ForegroundColor Cyan
$missing = @()
$stale = @()
foreach ($svc in $BackendServices) {
    $jar = "$ProjectRoot\$($svc.Name)\target\$($svc.Name)-0.1.0-SNAPSHOT.jar"
    if (-not (Test-Path $jar)) {
        $missing += $svc.Name
        continue
    }
    $latestSource = Get-ChildItem "$ProjectRoot\$($svc.Name)\src" -Recurse -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($latestSource -and $latestSource.LastWriteTime -gt (Get-Item $jar).LastWriteTime) {
        $stale += $svc.Name
    }
}
if ($missing.Count -eq 0 -and $stale.Count -eq 0) {
    Write-Host "      All jars exist, skip build" -ForegroundColor Green
} else {
    if ($missing.Count -gt 0) { Write-Host "      Missing jars: $($missing -join ', ')" -ForegroundColor Yellow }
    if ($stale.Count -gt 0) { Write-Host "      Stale jars: $($stale -join ', ')" -ForegroundColor Yellow }
    Write-Host "      Building..." -ForegroundColor Yellow
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
# 3. 启动全部服务（独立进程）
# ------------------------------------------------------------
Write-Host "`n[3/4] Starting services..." -ForegroundColor Cyan
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
# 4. 健康检查
# ------------------------------------------------------------
Write-Host "`n[4/4] Waiting for services to boot..." -ForegroundColor Cyan
$targets = $BackendServices | ForEach-Object { @{Label=$_.Name; Port=$_.Port; Url="http://localhost:$($_.Port)/actuator/health"} }
$targets += @(@{Label="campus-admin-web"; Port=$FrontendPort; Url="http://localhost:$FrontendPort"})

$up = @{}
$timeout = 90
$elapsed = 0
while ($elapsed -lt $timeout -and $up.Count -lt $targets.Count) {
    foreach ($t in $targets) {
        if ($up.ContainsKey($t.Port)) { continue }
        try {
            $res = Invoke-WebRequest -Uri $t.Url -TimeoutSec 1 -UseBasicParsing -SkipHttpErrorCheck -ErrorAction SilentlyContinue
            if ($res) {
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
