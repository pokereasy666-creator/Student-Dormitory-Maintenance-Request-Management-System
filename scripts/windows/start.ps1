# =====================================================================
# 启动学生宿舍报修管理系统（由 start.bat 调用）
#   1. 未安装依赖时自动执行 setup（创建虚拟环境、安装依赖）
#   2. 检查端口占用、MySQL 是否启动
#   3. 在当前窗口中启动 python run.py，等待网站可访问后自动打开浏览器
#   4. 启动失败时显示中文原因
# 停止：关闭本窗口或按 Ctrl+C；也可以运行 stop.bat
# =====================================================================
param(
    [switch]$NoBrowser,    # 不自动打开浏览器
    [switch]$NoMirror      # 自动安装依赖时不使用阿里云镜像
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'common.ps1')
Initialize-Console

$appDir     = $script:AppDir
$runPy      = Join-Path $appDir 'run.py'
$venvPython = Get-VenvPython $appDir
$logDir     = Join-Path $appDir 'logs'
$logPath    = Join-Path $logDir 'app.log'
$pidPath    = Join-Path $appDir 'app.pid'
Set-Location -LiteralPath $appDir

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "    学生宿舍报修管理系统（Python 版）- 启动" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "项目目录：$appDir"

# ---------- 1. 是否已经在运行 ----------
$config = Get-AppConfig $appDir
$url = "http://localhost:$($config.Port)"
if (Test-Path -LiteralPath $pidPath) {
    $oldPid = 0
    $pidText = [string](Get-Content -LiteralPath $pidPath -Raw -ErrorAction SilentlyContinue)
    [int]::TryParse($pidText.Trim(), [ref]$oldPid) | Out-Null
    if ($oldPid -gt 0 -and (Get-Process -Id $oldPid -ErrorAction SilentlyContinue) -and (Test-IsAppProcess $oldPid)) {
        Write-Step "系统已经在运行（进程号 $oldPid），无需重复启动"
        Write-Ok "访问地址：$url"
        if (-not $NoBrowser -and (Test-IsWindows)) { Start-Process $url }
        exit 0
    }
    Remove-Item -LiteralPath $pidPath -Force   # 上次异常退出留下的旧文件
}

# ---------- 2. 检查 Python 环境，未安装时自动安装 ----------
Write-Step "检查 Python 虚拟环境和依赖"
if (Test-VenvReady $appDir) {
    Write-Ok "运行环境已就绪"
} else {
    Write-Hint "首次运行（或依赖不完整），自动执行安装……"
    & (Join-Path $PSScriptRoot 'setup.ps1') -SkipDbInit -NoMirror:$NoMirror
    if ($LASTEXITCODE -ne 0 -or -not (Test-VenvReady $appDir)) {
        Write-Fail "运行环境安装失败，请按上方提示处理后重新运行 start.bat。"
        exit 1
    }
}

# ---------- 3. 检查端口 ----------
Write-Step "检查网站端口 $($config.Port) 是否空闲"
if (Test-TcpPort '127.0.0.1' $config.Port) {
    Write-Fail "端口 $($config.Port) 已被占用。"
    $ownerPid = Get-PortOwnerPid $config.Port
    if ($ownerPid) {
        $owner = Get-Process -Id $ownerPid -ErrorAction SilentlyContinue
        Write-Hint "占用端口的程序：$($owner.ProcessName)（进程号 $ownerPid）"
        if (Test-IsAppProcess $ownerPid) {
            Write-Hint "它就是本系统（可能在别的窗口中运行），可直接访问 $url ，或先运行 stop.bat 再启动。"
        }
    }
    Write-Hint "解决办法：关闭占用端口的程序（如 PyCharm 中正在运行的项目），或修改 config.ini 中 [server] 的 port。"
    exit 1
}
Write-Ok "端口 $($config.Port) 可用"

# ---------- 4. 检查 MySQL ----------
Write-Step "检查 MySQL 数据库（$($config.DbHost):$($config.DbPort)）"
if (-not (Test-TcpPort $config.DbHost $config.DbPort 3000)) {
    Show-MySqlHint $config.DbHost $config.DbPort
    exit 1
}
Write-Ok "MySQL 端口可以连接"

# ---------- 5. 启动 ----------
Write-Step "正在启动系统，首次启动会自动建库、建表，请稍候……"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$logOffset = 0
if (Test-Path -LiteralPath $logPath) { $logOffset = (Get-Item -LiteralPath $logPath).Length }
$stdoutPath = Join-Path $logDir 'console.out.log'
$stderrPath = Join-Path $logDir 'console.err.log'

# 为什么 Python 不直接往本窗口输出日志？
#   Windows 控制台默认开启"快速编辑模式"：用鼠标点一下黑窗口就会进入"选择"状态并暂停输出，
#   如果程序直接写控制台就会被卡住，网站随之无响应（答辩演示时很常见）。
#   所以 --no-console-log 让程序只写日志文件 logs\app.log，再由本脚本把日志内容显示到窗口；
#   即使窗口被"选择"暂停，也只是暂停显示，系统本身照常运行。
# PYTHONUTF8 / PYTHONIOENCODING：让 Python 的输出统一使用 UTF-8，避免中文乱码
$env:PYTHONUTF8 = '1'
$env:PYTHONIOENCODING = 'utf-8'
# 用 run.py 的完整路径启动：stop.bat 据此识别本系统的进程（路径有空格时需要加引号）
$proc = Start-Process -FilePath $venvPython -ArgumentList "`"$runPy`" --no-console-log" `
    -WorkingDirectory $appDir -NoNewWindow -PassThru -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath
Set-Content -LiteralPath $pidPath -Value $proc.Id -Encoding ASCII

# 把 logs\app.log 中新增的内容显示到窗口（类似 tail -f）
$script:tailPos = $logOffset
function Show-NewLog {
    if (-not (Test-Path -LiteralPath $logPath)) { return }
    $fs = $null
    try {
        # 以共享方式打开：Python 正在写这个文件
        $fs = New-Object System.IO.FileStream($logPath, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read,
            ([System.IO.FileShare]::ReadWrite -bor [System.IO.FileShare]::Delete))
        if ($fs.Length -lt $script:tailPos) { $script:tailPos = 0 }   # 日志超过 10MB 滚动后文件变小，从头读
        if ($fs.Length -eq $script:tailPos) { return }
        [void]$fs.Seek($script:tailPos, [System.IO.SeekOrigin]::Begin)
        $buffer = New-Object byte[] ([int]($fs.Length - $script:tailPos))
        $count = $fs.Read($buffer, 0, $buffer.Length)
        $script:tailPos += $count
        Write-Host -NoNewline ([System.Text.Encoding]::UTF8.GetString($buffer, 0, $count))
    } catch {
        # 读取失败（如文件正被滚动）时跳过，下次再读
    } finally {
        if ($fs) { $fs.Dispose() }
    }
}

try {
    # 等待网站可以访问：最多 60 秒；期间如果进程退出说明启动失败
    $ready = $false
    $deadline = (Get-Date).AddSeconds(60)
    while ((Get-Date) -lt $deadline) {
        Show-NewLog
        if ($proc.HasExited) { break }
        if (Test-AppReady $config.Port) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    Show-NewLog

    if ($ready) {
        Write-Host ""
        Write-Host "==================================================" -ForegroundColor Green
        Write-Host "  系统启动成功！" -ForegroundColor Green
        Write-Host "  本机访问：  $url" -ForegroundColor Green
        foreach ($ip in Get-LanIPv4) {
            Write-Host "  局域网访问：http://$ip`:$($config.Port) （手机需连接同一 Wi-Fi）" -ForegroundColor Green
        }
        Write-Host "  维修人员账号：W001 / W002 / W003，初始密码 123456" -ForegroundColor Green
        Write-Host "  演示学生账号：2023001，初始密码 123456" -ForegroundColor Green
        Write-Host "  运行日志：  $logPath" -ForegroundColor Green
        Write-Host "  停止系统：关闭本窗口，或按 Ctrl+C" -ForegroundColor Green
        Write-Host "  不要关闭本窗口，否则系统会停止运行" -ForegroundColor Yellow
        Write-Host "==================================================" -ForegroundColor Green
        if (-not $NoBrowser -and (Test-IsWindows)) { Start-Process $url }
    } elseif ($proc.HasExited) {
        Write-Host ""
        Write-Fail "系统启动失败，原因见上方日志中的'启动失败'一行。"
        # Python 本身出错（如语法错误、缺少模块）时，错误信息只在错误输出中，没有写进日志
        if (Test-Path -LiteralPath $stderrPath) {
            $errText = [System.IO.File]::ReadAllText($stderrPath, [System.Text.Encoding]::UTF8)
            if ($errText.Trim() -and -not $errText.Contains('启动失败')) { Write-Host $errText -ForegroundColor Red }
        }
        Write-Hint "按提示用记事本修改 $($config.Path) 后，重新运行 start.bat。"
        exit 1
    } else {
        Write-Hint "等待 60 秒后网站仍不可访问，请查看上方日志或 $logPath 。系统仍在后台尝试启动。"
    }

    # 保持窗口：持续显示新日志，直到程序结束（关闭窗口或 Ctrl+C 时程序会一起退出）
    while (-not $proc.WaitForExit(1000)) { Show-NewLog }
    Show-NewLog
    Write-Host ""
    Write-Host "系统已停止。"
} finally {
    Remove-Item -LiteralPath $pidPath -Force -ErrorAction SilentlyContinue
}
