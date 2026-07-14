# CampusMind AI — 停止所有开发服务
$ports = 8080..8089 + 5173
foreach ($p in $ports) {
    $line = netstat -ano | Select-String ":$p .*LISTENING"
    if ($line) {
        $procId = ($line -split '\s+')[-1]
        Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
        Write-Host "Stopped PID $procId (port $p)" -ForegroundColor Yellow
    }
}
Get-Process campus_flutter_app -ErrorAction SilentlyContinue |
    Stop-Process -Force -ErrorAction SilentlyContinue
Write-Host "Done." -ForegroundColor Green
