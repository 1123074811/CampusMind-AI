param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AdminUsername = "admin",
    [string]$AdminPassword = "admin"
)

$ErrorActionPreference = "Stop"
$runId = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$username = "e2e_$runId"
$password = "E2ePass123!"
$email = "$username@example.test"
$settings = @{}
Get-Content (Join-Path $PSScriptRoot "..\.env") | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]*)=(.*)$') { $settings[$matches[1].Trim()] = $matches[2].Trim() }
}

function Invoke-Json {
    param([string]$Method, [string]$Path, [object]$Body, [string]$Token, [Microsoft.PowerShell.Commands.WebRequestSession]$Session)
    $headers = @{ Accept = "application/json" }
    if ($Token) { $headers.Authorization = "Bearer $Token" }
    $params = @{ Method = $Method; Uri = "$BaseUrl$Path"; Headers = $headers; ContentType = "application/json" }
    if ($null -ne $Body) { $params.Body = ($Body | ConvertTo-Json -Depth 10 -Compress) }
    if ($Session) { $params.WebSession = $Session }
    $response = Invoke-RestMethod @params
    if (-not $response.success) { throw "$Path failed: $($response.message)" }
    return $response.data
}

function Invoke-MySqlScalar {
    param([string]$Sql)
    $env:MYSQL_PWD = $settings['MYSQL_PASSWORD']
    $result = & mysql --batch --skip-column-names --protocol=tcp --host=127.0.0.1 --port=13306 `
        "--user=$($settings['MYSQL_USERNAME'])" "--database=$($settings['MYSQL_DATABASE'])" --execute=$Sql
    if ($LASTEXITCODE -ne 0) { throw "MySQL E2E setup failed" }
    return @($result)[-1]
}

Write-Host "[1/10] Registering isolated student $username"
$login = Invoke-Json POST "/api/v1/auth/register" @{ username=$username; email=$email; password=$password }
$token = $login.accessToken
if (-not $token) { throw "Registration did not return an access token" }

try {
    Write-Host "[2/10] Verifying profile and privacy consent"
    $me = Invoke-Json GET "/api/v1/users/me" $null $token
    if ($me.username -ne $username) { throw "Profile identity mismatch" }
    Invoke-Json POST "/api/v1/users/me/privacy/consents" @{
        consentType="PRIVACY_POLICY"; granted=$true; policyVersion="2026-07-01"; source="E2E"
    } $token | Out-Null
    $privacy = Invoke-Json GET "/api/v1/users/me/privacy" $null $token
    if (-not $privacy.consents) { throw "Consent history is empty" }

    Write-Host "[3/10] Registering notification device"
    Invoke-Json PUT "/api/v1/information/notifications/devices" @{
        deviceId="e2e-device-$runId"; platform="E2E"; pushToken=$null
    } $token | Out-Null

    Write-Host "[4/10] Verifying scheduled delivery, deduplication and withdrawal"
    $reminderId = [long](Invoke-MySqlScalar @"
INSERT INTO user_action_item(user_id, information_item_id, title, due_at, status)
SELECT $($me.id), id, 'E2E notification $runId', DATE_ADD(NOW(), INTERVAL 2 DAY), 'CONFIRMED'
FROM information_item ORDER BY id LIMIT 1;
SET @action_id = LAST_INSERT_ID();
INSERT INTO user_reminder(action_item_id, user_id, remind_at, status)
VALUES (@action_id, $($me.id), DATE_SUB(NOW(), INTERVAL 1 SECOND), 'PENDING');
SELECT LAST_INSERT_ID();
"@)
    $deadline = (Get-Date).AddSeconds(75)
    $delivery = $null
    do {
        Start-Sleep -Seconds 3
        $deliveries = @(Invoke-Json GET "/api/v1/information/notifications/deliveries" $null $token)
        $delivery = $deliveries | Where-Object { $_.reminderId -eq $reminderId -and $_.channel -eq 'IN_APP' } | Select-Object -First 1
    } while (($null -eq $delivery -or $delivery.status -ne 'SENT') -and (Get-Date) -lt $deadline)
    if ($null -eq $delivery -or $delivery.status -ne 'SENT') { throw "Scheduled in-app delivery was not sent" }
    if (@($deliveries | Where-Object { $_.reminderId -eq $reminderId -and $_.channel -eq 'IN_APP' }).Count -ne 1) {
        throw "Notification delivery was not deduplicated"
    }
    Invoke-Json PUT "/api/v1/information/notifications/reminders/$reminderId/withdraw" $null $token | Out-Null
    $withdrawn = @(Invoke-Json GET "/api/v1/information/notifications/deliveries" $null $token) |
        Where-Object { $_.reminderId -eq $reminderId -and $_.channel -eq 'IN_APP' } | Select-Object -First 1
    if ($withdrawn.status -ne 'WITHDRAWN') { throw "Delivery withdrawal was not persisted" }

    Write-Host "[5/10] Exercising feed and search through gateway"
    Invoke-Json GET "/api/v1/information/feed?size=3" $null $token | Out-Null
    $search = Invoke-Json GET "/api/v1/search?query=%E6%A0%A1%E5%9B%AD%E9%80%9A%E7%9F%A5" $null $token
    if ($null -eq $search.fallback -or -not $search.mode) { throw "Search degradation metadata missing" }

    Write-Host "[6/10] Verifying complete personal-data export"
    $export = Invoke-Json GET "/api/v1/users/me/export" $null $token
    if ($null -eq $export.consentHistory -or $null -eq $export.devices -or $null -eq $export.notificationDeliveries) {
        throw "Privacy lifecycle data missing from export"
    }

    Write-Host "[7/10] Verifying HttpOnly admin cookie session"
    $web = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
    Invoke-Json POST "/api/v1/auth/web/login" @{ username=$AdminUsername; password=$AdminPassword } "" $web | Out-Null
    if ($web.Cookies.GetCookies($BaseUrl)["campusmind_access"].HttpOnly -ne $true) { throw "Access cookie is not HttpOnly" }
    $admin = Invoke-Json GET "/api/admin/dashboard" $null "" $web
    if ($null -eq $admin.metrics) { throw "Admin dashboard unavailable through cookie session" }

    Write-Host "[8/10] Verifying data-source version endpoint"
    if ($admin.dataSources.Count -gt 0) {
        Invoke-Json GET "/api/admin/sources/$($admin.dataSources[0].id)/versions" $null "" $web | Out-Null
    }
    Write-Host "[9/10] Revoking admin web session"
    Invoke-Json POST "/api/v1/auth/web/logout" $null "" $web | Out-Null
}
finally {
    Write-Host "[10/10] Deleting isolated student and revoking its sessions"
    if ($token) { Invoke-Json DELETE "/api/v1/users/me" @{ password=$password } $token | Out-Null }
    Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
}

Write-Host "Core API E2E passed." -ForegroundColor Green
