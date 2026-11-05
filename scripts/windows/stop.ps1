# =====================================================================
# 停止学生宿舍报修管理系统（由 stop.bat 调用）
# 查找顺序：app.pid 记录的进程 → 占用网站端口的进程。
# 只会停止命令行中包含本项目 run.py 完整路径的进程，不会误杀其他 Python 程序。
# 说明：Windows 上虚拟环境的 python.exe 是一个"启动器"，真正运行网站的是它启动的子进程，
#       所以 app.pid 中的进程和占用端口的进程可能不是同一个，两个都会停止。
# =====================================================================
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'common.ps1')
Initialize-Console

$appDir  = $script:AppDir
$pidPath = Join-Path $appDir 'app.pid'
$config  = Get-AppConfig $appDir

Write-Step "停止学生宿舍报修管理系统"

$targets = @()
# 1. 占用网站端口的进程（真正运行网站的进程，先停止它）
$ownerPid = Get-PortOwnerPid $config.Port
if ($ownerPid) {
    if (Test-IsAppProcess $ownerPid) {
        $targets += $ownerPid
    } else {
        $owner = Get-Process -Id $ownerPid -ErrorAction SilentlyContinue
        Write-Hint "端口 $($config.Port) 被其他程序占用：$($owner.ProcessName)（进程号 $ownerPid），它不是本系统，未做处理。"
    }
}
# 2. 启动脚本记录的进程号
if (Test-Path -LiteralPath $pidPath) {
    $id = 0
    $pidText = [string](Get-Content -LiteralPath $pidPath -Raw -ErrorAction SilentlyContinue)
    if ([int]::TryParse($pidText.Trim(), [ref]$id) -and ($targets -notcontains $id) -and
        (Get-Process -Id $id -ErrorAction SilentlyContinue) -and (Test-IsAppProcess $id)) {
        $targets += $id
    }
}

if ($targets.Count -eq 0) {
    Write-Ok "系统当前没有在运行。"
} else {
    foreach ($id in $targets) {
        Stop-Process -Id $id -Force -ErrorAction SilentlyContinue
        Write-Ok "已停止进程 $id"
    }
}
Remove-Item -LiteralPath $pidPath -Force -ErrorAction SilentlyContinue
