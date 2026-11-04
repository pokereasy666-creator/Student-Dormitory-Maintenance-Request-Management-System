# =====================================================================
# 启动学生宿舍报修管理系统（由 start.bat 调用，也可单独运行）
#   1. 检查 Java 17+、端口占用、MySQL 是否启动
#   2. 在当前窗口中启动 java -jar dorm-repair.jar
#   3. 等待网站可访问后自动打开浏览器
#   4. 启动失败时分析日志，给出中文提示
# 停止：关闭本窗口或按 Ctrl+C；也可以运行 stop.bat
# =====================================================================
param(
    [switch]$NoBrowser     # 不自动打开浏览器
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'common.ps1')
Initialize-Console

$appDir     = Split-Path -Parent $PSScriptRoot
$jarName    = 'dorm-repair.jar'
$jarPath    = Join-Path $appDir $jarName
$configPath = Join-Path $appDir 'application.properties'
$logPath    = Join-Path (Join-Path $appDir 'logs') 'app.log'
$pidPath    = Join-Path $appDir 'app.pid'
Set-Location -LiteralPath $appDir

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "        学生宿舍报修管理系统 - 启动" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "程序目录：$appDir"

# ---------- 0. 读取配置 ----------
if (-not (Test-Path -LiteralPath $jarPath)) {
    Write-Fail "未找到 $jarName，请确认本脚本位于部署目录中（与 $jarName 同一文件夹的 scripts 子目录）。"
    exit 1
}
$config = Read-Properties $configPath
$port   = 8080;        if ($config['server.port']) { $port = [int]$config['server.port'] }
$dbHost = 'localhost'; if ($config['DB_HOST'])     { $dbHost = $config['DB_HOST'] }
$dbPort = 3306;        if ($config['DB_PORT'])     { $dbPort = [int]$config['DB_PORT'] }
$url    = "http://localhost:$port"

# ---------- 1. 是否已经在运行 ----------
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

# ---------- 2. 检查 Java ----------
Write-Step "检查 Java 环境（需要 17 及以上版本）"
$java = Find-Java -MinMajor 17
if (-not $java) {
    Write-Fail "未找到 Java 17 或更高版本。"
    Write-Hint "请安装 JDK 17（推荐 Eclipse Temurin 17），安装时勾选 'Set JAVA_HOME' 和 'Add to PATH'，"
    Write-Hint "安装完成后重新打开本窗口再试。详见 docs/windows-deploy.md 第 1 节。"
    exit 1
}
Write-Ok "Java $($java.Version)  ->  $($java.Exe)"

# ---------- 3. 检查端口 ----------
Write-Step "检查网站端口 $port 是否空闲"
if (Test-TcpPort '127.0.0.1' $port) {
    Write-Fail "端口 $port 已被占用。"
    $ownerPid = Get-PortOwnerPid $port
    if ($ownerPid) {
        $owner = Get-Process -Id $ownerPid -ErrorAction SilentlyContinue
        Write-Hint "占用端口的程序：$($owner.ProcessName)（进程号 $ownerPid）"
        if (Test-IsAppProcess $ownerPid) {
            Write-Hint "它就是本系统（可能在别的窗口或 IDEA 中运行），可直接访问 $url ，或先运行 stop.bat 再启动。"
        }
    }
    Write-Hint "解决办法：关闭占用端口的程序（如 IDEA 中正在运行的项目），或修改 application.properties 中的 server.port。"
    exit 1
}
Write-Ok "端口 $port 可用"

# ---------- 4. 检查 MySQL ----------
Write-Step "检查 MySQL 数据库（$dbHost`:$dbPort）"
if (-not (Test-TcpPort $dbHost $dbPort 3000)) {
    Write-Fail "无法连接到 MySQL $dbHost`:$dbPort，MySQL 可能没有启动。"
    if (Test-IsWindows) {
        $services = Get-Service -ErrorAction SilentlyContinue | Where-Object { $_.Name -like 'MySQL*' -or $_.Name -like 'MariaDB*' }
        if ($services) {
            foreach ($s in $services) { Write-Hint "检测到数据库服务：$($s.Name)，当前状态：$($s.Status)" }
            Write-Hint "启动方法：Win+R 输入 services.msc，找到上面的服务，右键 -> 启动；"
            Write-Hint "或以管理员身份打开命令提示符，执行：net start $($services[0].Name)"
        } else {
            Write-Hint "本机没有检测到 MySQL 服务，请先安装 MySQL 8（详见 docs/windows-deploy.md 第 1 节）。"
        }
    }
    Write-Hint "如果 MySQL 不在本机或端口不是 3306，请修改 application.properties 中的 DB_HOST / DB_PORT。"
    exit 1
}
Write-Ok "MySQL 端口可以连接"

# ---------- 5. 启动 ----------
Write-Step "正在启动系统，首次启动会自动建库、建表，请稍候……"
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $logPath) | Out-Null
$logOffset = 0
if (Test-Path -LiteralPath $logPath) { $logOffset = (Get-Item -LiteralPath $logPath).Length }

# 为什么 java 不直接往本窗口输出日志？
#   Windows 控制台默认开启"快速编辑模式"：用鼠标点一下黑窗口就会进入"选择"状态并暂停输出，
#   如果 java 直接写控制台，就会被卡住，网站随之无响应（答辩演示时很常见）。
#   所以让 java 只写日志文件（logs\app.log），再由本脚本把日志内容显示到窗口；
#   即使窗口被"选择"暂停，也只是暂停显示，系统本身照常运行。
# -Dfile.encoding=UTF-8：日志文件统一使用 UTF-8 编码（Java 17 在中文 Windows 上默认是 GBK）
# -Dlogging.threshold.console=OFF：关闭 Spring Boot 的控制台日志（日志只写入 logs\app.log）
$logDir = Split-Path -Parent $logPath
$stdoutPath = Join-Path $logDir 'console.out.log'
$stderrPath = Join-Path $logDir 'console.err.log'
$javaArgs = @('-Dfile.encoding=UTF-8', '-Dlogging.threshold.console=OFF', '-jar', $jarName)
$proc = Start-Process -FilePath $java.Exe -ArgumentList $javaArgs -WorkingDirectory $appDir -NoNewWindow -PassThru `
    -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath
Set-Content -LiteralPath $pidPath -Value $proc.Id -Encoding ASCII

# 把 logs\app.log 中新增的内容显示到窗口（类似 tail -f）
$script:tailPos = $logOffset
function Show-NewLog {
    if (-not (Test-Path -LiteralPath $logPath)) { return }
    $fs = $null
    try {
        # 以共享方式打开：java 正在写这个文件
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
    # 等待网站可以访问：最多 120 秒；期间如果 java 进程退出说明启动失败
    $ready = $false
    $deadline = (Get-Date).AddSeconds(120)
    while ((Get-Date) -lt $deadline) {
        Show-NewLog
        if ($proc.HasExited) { break }
        if (Test-AppReady $port) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    Show-NewLog

    if ($ready) {
        Write-Host ""
        Write-Host "==================================================" -ForegroundColor Green
        Write-Host "  系统启动成功！" -ForegroundColor Green
        Write-Host "  本机访问：  $url" -ForegroundColor Green
        foreach ($ip in Get-LanIPv4) {
            Write-Host "  局域网访问：http://$ip`:$port （手机需连接同一 Wi-Fi）" -ForegroundColor Green
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
        Write-Fail "系统启动失败。"
        # 只分析本次启动新写入的日志，以及 java 的错误输出
        $newLog = ''
        if (Test-Path -LiteralPath $logPath) {
            $bytes = [System.IO.File]::ReadAllBytes($logPath)
            if ($bytes.Length -gt $logOffset) {
                $newLog = [System.Text.Encoding]::UTF8.GetString($bytes, [int]$logOffset, $bytes.Length - [int]$logOffset)
            }
        }
        $errText = ''
        if (Test-Path -LiteralPath $stderrPath) { $errText = [string](Get-Content -LiteralPath $stderrPath -Raw) }
        if ($errText.Trim()) {
            Write-Host $errText -ForegroundColor Red
            $newLog = $newLog + $errText
        }
        if ($newLog -match 'Access denied for user') {
            Write-Hint "原因：MySQL 用户名或密码错误。"
            Write-Hint "请用记事本打开 application.properties，修改 DB_USERNAME 和 DB_PASSWORD 后重新运行 start.bat。"
        } elseif ($newLog -match 'Communications link failure|Connection refused') {
            Write-Hint "原因：无法连接 MySQL。请确认 MySQL 服务已启动，DB_HOST / DB_PORT 配置正确。"
        } elseif ($newLog -match 'already in use') {
            Write-Hint "原因：端口 $port 被占用。请修改 application.properties 中的 server.port。"
        } elseif ($newLog -match 'Unknown database|command denied') {
            Write-Hint "原因：数据库 dorm_repair 不存在，且当前 MySQL 账号没有建库权限。"
            Write-Hint "请用 root 账号，或先手动执行：CREATE DATABASE dorm_repair DEFAULT CHARSET utf8mb4;"
        } else {
            Write-Hint "请查看上方输出或日志文件 $logPath 中的 ERROR 信息。"
        }
        exit 1
    } else {
        Write-Hint "等待 120 秒后网站仍不可访问，请查看上方输出或日志 $logPath 。系统仍在后台尝试启动。"
    }

    # 保持窗口：持续显示新日志，直到 java 进程结束（关闭窗口或 Ctrl+C 时 java 会一起退出）
    while (-not $proc.WaitForExit(1000)) { Show-NewLog }
    Show-NewLog
    Write-Host ""
    Write-Host "系统已停止。"
} finally {
    Remove-Item -LiteralPath $pidPath -Force -ErrorAction SilentlyContinue
}
