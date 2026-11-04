# =====================================================================
# 停止学生宿舍报修管理系统（由 stop.bat 调用）
# 查找顺序：app.pid 记录的进程 → 占用网站端口的进程。
# 只会停止命令行中包含 dorm-repair.jar 的 java 进程，不会误杀其他程序。
# =====================================================================
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'common.ps1')
Initialize-Console

$appDir  = Split-Path -Parent $PSScriptRoot
$pidPath = Join-Path $appDir 'app.pid'
$config  = Read-Properties (Join-Path $appDir 'application.properties')
$port    = 8080; if ($config['server.port']) { $port = [int]$config['server.port'] }

Write-Step "停止学生宿舍报修管理系统"

$targets = @()
# 1. 启动脚本记录的进程号
if (Test-Path -LiteralPath $pidPath) {
    $id = 0
    $pidText = [string](Get-Content -LiteralPath $pidPath -Raw -ErrorAction SilentlyContinue)
    if ([int]::TryParse($pidText.Trim(), [ref]$id) -and (Get-Process -Id $id -ErrorAction SilentlyContinue) -and (Test-IsAppProcess $id)) {
        $targets += $id
    }
}
# 2. 占用网站端口的进程（例如没有通过 start.bat 启动的情况）
$ownerPid = Get-PortOwnerPid $port
if ($ownerPid -and ($targets -notcontains $ownerPid)) {
    if (Test-IsAppProcess $ownerPid) {
        $targets += $ownerPid
    } else {
        $owner = Get-Process -Id $ownerPid -ErrorAction SilentlyContinue
        Write-Hint "端口 $port 被其他程序占用：$($owner.ProcessName)（进程号 $ownerPid），它不是本系统，未做处理。"
    }
}

if ($targets.Count -eq 0) {
    Write-Ok "系统当前没有在运行。"
} else {
    foreach ($id in $targets) {
        Stop-Process -Id $id -Force
        Write-Ok "已停止进程 $id"
    }
}
Remove-Item -LiteralPath $pidPath -Force -ErrorAction SilentlyContinue
