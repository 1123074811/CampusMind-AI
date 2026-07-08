# CampusMind AI — 停止所有开发服务
$ports = 8080..8089 + 5173
foreach ($p in $ports) {
    $line = netstat -ano | Select-String ":$p .*LISTENING"
    if ($line) {
        $pid = ($line -split '\s+')[-1]
        Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue
        Write-Host "Stopped PID $pid (port $p)" -ForegroundColor Yellow
    }
}
Get-Process -Name java -ErrorAction SilentlyContinue | Stop-Process -Force
Get-Process -Name node -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Write-Host "Done." -ForegroundColor Green
