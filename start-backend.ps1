# 启动后端，关闭窗口时自动杀进程
$ErrorActionPreference = "Stop"

# 先清掉旧进程
Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue | ForEach-Object {
    Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue
}

Write-Host "正在启动后端..."
$process = Start-Process -FilePath "mvn" -ArgumentList "spring-boot:run" -WorkingDirectory "$PSScriptRoot\backend" -PassThru -NoNewWindow

Write-Host "后端启动中 (PID: $($process.Id))，关闭此窗口会自动停止..."

# 监听窗口关闭 —— 这个脚本退出时杀掉 Java
Register-EngineEvent -SourceIdentifier PowerShell.Exiting -Action {
    Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue | ForEach-Object {
        Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue
    }
} | Out-Null

try {
    $process.WaitForExit()
} finally {
    Write-Host "正在停止 Java 进程..."
    Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue | ForEach-Object {
        Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue
    }
    Write-Host "已停止。"
}
