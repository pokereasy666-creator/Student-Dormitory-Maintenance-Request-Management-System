# =====================================================================
# 公共函数：setup.ps1 / start.ps1 / stop.ps1 / package.ps1 共用
# 兼容 Windows 自带的 PowerShell 5.1（不使用 ?? 、三元运算符等 PowerShell 7 新语法）
# 注意：本文件必须保存为 "UTF-8 with BOM"，否则 PowerShell 5.1 会把中文读成乱码。
# =====================================================================

# 项目根目录（本文件位于 scripts\windows）
$script:AppDir = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

# 最低 Python 版本 3.10
$script:MinPythonMinor = 10

# 让控制台正确显示中文
function Initialize-Console {
    try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }
}

function Write-Step([string]$Message) { Write-Host ""; Write-Host "==> $Message" -ForegroundColor Cyan }
function Write-Ok([string]$Message)   { Write-Host "    [OK] $Message" -ForegroundColor Green }
function Write-Hint([string]$Message) { Write-Host "    $Message" -ForegroundColor Yellow }
function Write-Fail([string]$Message) { Write-Host "    [失败] $Message" -ForegroundColor Red }

# 是否运行在 Windows 上（PowerShell 5.1 没有 $IsWindows 变量，用环境变量判断）
function Test-IsWindows { return $env:OS -eq 'Windows_NT' }

# ---------------------------------------------------------------------
# 运行外部程序
# ---------------------------------------------------------------------

# 运行程序并等待结束，返回退出码和输出（标准输出 + 错误输出）；超时会结束该程序。
# 不直接用 & 调用，是因为 PowerShell 5.1 在捕获外部程序的错误输出时容易误报异常。
function Invoke-Native([string]$Exe, [string]$Arguments, [int]$TimeoutSec = 60) {
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $Exe
    $psi.Arguments = $Arguments
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.CreateNoWindow = $true
    # 统一用 UTF-8：用户名是中文时（C:\Users\张三\...），Python 默认按 GBK 输出路径，会被解码成乱码
    $psi.StandardOutputEncoding = [System.Text.Encoding]::UTF8
    $psi.StandardErrorEncoding = [System.Text.Encoding]::UTF8
    $psi.EnvironmentVariables['PYTHONIOENCODING'] = 'utf-8'
    try {
        $proc = [System.Diagnostics.Process]::Start($psi)
    } catch {
        return [pscustomobject]@{ ExitCode = -1; Output = $_.Exception.Message }
    }
    $stdoutTask = $proc.StandardOutput.ReadToEndAsync()
    $stderrTask = $proc.StandardError.ReadToEndAsync()
    if (-not $proc.WaitForExit($TimeoutSec * 1000)) {
        try { $proc.Kill() } catch { }
        return [pscustomobject]@{ ExitCode = -1; Output = '超时' }
    }
    return [pscustomobject]@{ ExitCode = $proc.ExitCode; Output = ($stdoutTask.Result + $stderrTask.Result) }
}

# ---------------------------------------------------------------------
# Python 检测
# ---------------------------------------------------------------------

# 运行 python 打印版本号和真实路径；无法运行（如微软商店的"假 python"）返回 $null
function Get-PythonInfo([string]$Exe, [string]$PrefixArgs = '') {
    $result = Invoke-Native $Exe "$PrefixArgs -c `"import sys;print(*sys.version_info[:3]);print(sys.executable)`"" 20
    if ($result.ExitCode -ne 0) { return $null }
    $lines = @($result.Output -split "`r?`n" | Where-Object { $_.Trim() -ne '' })
    if ($lines.Count -lt 2 -or $lines[0] -notmatch '^(\d+) (\d+) (\d+)$') { return $null }
    # Command + PrefixArgs 是探测时使用的命令（如 py -3），后续创建虚拟环境也用它，不依赖解析出来的路径
    return [pscustomobject]@{
        Major = [int]$Matches[1]; Minor = [int]$Matches[2]
        Version = "$($Matches[1]).$($Matches[2]).$($Matches[3])"
        Exe = $lines[1].Trim()
        Command = $Exe; PrefixArgs = $PrefixArgs
    }
}

# 查找 Python 3.10 及以上版本。查找顺序：
#   py 启动器（python.org 安装包自带）→ PATH 中的 python / python3 → 常见安装目录
# 找不到时打印检测到的版本，便于排查，并返回 $null
function Find-Python {
    $probes = New-Object System.Collections.Generic.List[object]
    $py = Get-Command py -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($py) { $probes.Add(@($py.Path, '-3')) }
    foreach ($name in @('python', 'python3')) {
        Get-Command $name -CommandType Application -All -ErrorAction SilentlyContinue | ForEach-Object { $probes.Add(@($_.Path, '')) }
    }
    if (Test-IsWindows) {
        $patterns = @(
            (Join-Path $env:LOCALAPPDATA 'Programs\Python\Python3*\python.exe'),   # 默认"仅为我安装"的位置
            (Join-Path $env:ProgramFiles 'Python3*\python.exe'),                   # "为所有用户安装"的位置
            'C:\Python3*\python.exe'
        )
        foreach ($pattern in $patterns) {
            Get-ChildItem -Path $pattern -ErrorAction SilentlyContinue | ForEach-Object { $probes.Add(@($_.FullName, '')) }
        }
    }
    $seen = @{}
    $checked = @()
    foreach ($probe in $probes) {
        $key = "$($probe[0])|$($probe[1])"
        if ($seen.ContainsKey($key)) { continue }
        $seen[$key] = $true
        $info = Get-PythonInfo $probe[0] $probe[1]
        if (-not $info) { continue }
        $checked += $info
        if ($info.Major -eq 3 -and $info.Minor -ge $script:MinPythonMinor) { return $info }
    }
    if ($checked.Count -gt 0) {
        Write-Hint "检测到以下 Python，但版本都低于 3.$($script:MinPythonMinor)："
        foreach ($i in $checked) { Write-Hint "  - Python $($i.Version)  $($i.Exe)" }
    }
    return $null
}

# 虚拟环境 .venv 中的 python 路径（Windows 与 Linux/macOS 目录结构不同）
function Get-VenvPython([string]$AppDir = $script:AppDir) {
    if (Test-IsWindows) { return Join-Path $AppDir '.venv\Scripts\python.exe' }
    return Join-Path (Join-Path (Join-Path $AppDir '.venv') 'bin') 'python'
}

# 虚拟环境是否可用，且依赖已全部安装
function Test-VenvReady([string]$AppDir = $script:AppDir) {
    $venvPython = Get-VenvPython $AppDir
    if (-not (Test-Path -LiteralPath $venvPython)) { return $false }
    $result = Invoke-Native $venvPython '-c "import flask, flask_sqlalchemy, flask_wtf, pymysql, cryptography, waitress"' 60
    return $result.ExitCode -eq 0
}

# ---------------------------------------------------------------------
# 配置文件 / 网络 / 进程
# ---------------------------------------------------------------------

# 读取 .ini 配置文件，返回 $ini['节名']['键名']；忽略 ; 和 # 开头的注释行
function Read-Ini([string]$Path) {
    $ini = @{}
    if (-not (Test-Path -LiteralPath $Path)) { return $ini }
    $section = ''
    foreach ($line in [System.IO.File]::ReadAllLines($Path)) {
        $t = $line.Trim().TrimStart([char]0xFEFF)
        if ($t -eq '' -or $t.StartsWith(';') -or $t.StartsWith('#')) { continue }
        if ($t -match '^\[(.+)\]$') { $section = $Matches[1].Trim(); $ini[$section] = @{}; continue }
        $idx = $t.IndexOf('=')
        if ($idx -lt 1 -or -not $section) { continue }
        $ini[$section][$t.Substring(0, $idx).Trim()] = $t.Substring($idx + 1).Trim()
    }
    return $ini
}

# 读取 ini 中的值，不存在时返回默认值
function Get-IniValue($Ini, [string]$Section, [string]$Key, [string]$Default) {
    if ($Ini.ContainsKey($Section) -and $Ini[$Section].ContainsKey($Key) -and $Ini[$Section][$Key]) {
        return $Ini[$Section][$Key]
    }
    return $Default
}

# 确保 config.ini 存在（不存在时从 config.example.ini 复制），返回读取结果
function Get-AppConfig([string]$AppDir = $script:AppDir) {
    $configPath = Join-Path $AppDir 'config.ini'
    if (-not (Test-Path -LiteralPath $configPath)) {
        Copy-Item -LiteralPath (Join-Path $AppDir 'config.example.ini') -Destination $configPath
        Write-Hint "已生成配置文件 config.ini（数据库密码默认为 root，如不同请用记事本修改）"
    }
    $ini = Read-Ini $configPath
    return [pscustomobject]@{
        Path   = $configPath
        DbHost = Get-IniValue $ini 'database' 'host' 'localhost'
        DbPort = [int](Get-IniValue $ini 'database' 'port' '3306')
        Port   = [int](Get-IniValue $ini 'server' 'port' '5000')
    }
}

# 测试能否连上某个 TCP 端口（用于检查 MySQL 是否启动、网站端口是否被占用）
function Test-TcpPort([string]$HostName, [int]$Port, [int]$TimeoutMs = 2000) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect($HostName, $Port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne($TimeoutMs)) { return $false }
        $client.EndConnect($async)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

# MySQL 端口连不上时，给出 Windows 服务状态和启动方法
function Show-MySqlHint([string]$DbHost, [int]$DbPort) {
    Write-Fail "无法连接到 MySQL $DbHost`:$DbPort，MySQL 可能没有启动。"
    if (Test-IsWindows) {
        $services = Get-Service -ErrorAction SilentlyContinue | Where-Object { $_.Name -like 'MySQL*' -or $_.Name -like 'MariaDB*' }
        if ($services) {
            foreach ($s in $services) { Write-Hint "检测到数据库服务：$($s.Name)，当前状态：$($s.Status)" }
            Write-Hint "启动方法：Win+R 输入 services.msc，找到上面的服务，右键 -> 启动；"
            Write-Hint "或以管理员身份打开命令提示符，执行：net start $($services[0].Name)"
        } else {
            Write-Hint "本机没有检测到 MySQL 服务，请先安装 MySQL 8（详见 docs\windows-deploy.md 第 1 节）。"
        }
    }
    Write-Hint "如果 MySQL 不在本机或端口不是 3306，请修改 config.ini 中 [database] 的 host / port。"
}

# 网站是否已经可以访问（登录页返回 200）
function Test-AppReady([int]$Port) {
    try {
        $req = [System.Net.HttpWebRequest]::Create("http://127.0.0.1:$Port/login")
        $req.Proxy = $null
        $req.Timeout = 3000
        $req.AllowAutoRedirect = $false
        $resp = $req.GetResponse()
        $code = [int]$resp.StatusCode
        $resp.Close()
        return $code -eq 200
    } catch {
        return $false
    }
}

# 获取进程的完整命令行（用于确认某个进程确实是本系统，避免误杀其他程序）
function Get-ProcessCommandLine([int]$ProcessId) {
    if (Test-IsWindows) {
        try { return (Get-CimInstance Win32_Process -Filter "ProcessId=$ProcessId" -ErrorAction Stop).CommandLine } catch { }
        try { return (Get-WmiObject Win32_Process -Filter "ProcessId=$ProcessId" -ErrorAction Stop).CommandLine } catch { }
        return $null
    }
    try { return (Get-Process -Id $ProcessId -ErrorAction Stop).CommandLine } catch { return $null }
}

# 判断进程是否是本系统：命令行中包含本项目 run.py 的完整路径
function Test-IsAppProcess([int]$ProcessId, [string]$AppDir = $script:AppDir) {
    $cmdLine = Get-ProcessCommandLine $ProcessId
    $runPy = Join-Path $AppDir 'run.py'
    return [bool]($cmdLine -and $cmdLine.IndexOf($runPy, [System.StringComparison]::OrdinalIgnoreCase) -ge 0)
}

# 查询正在监听某端口的进程 ID（仅 Windows）；没有则返回 $null
function Get-PortOwnerPid([int]$Port) {
    if (-not (Test-IsWindows)) { return $null }
    try {
        $conn = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction Stop | Select-Object -First 1
        if ($conn) { return [int]$conn.OwningProcess }
    } catch { }
    # 旧系统没有 Get-NetTCPConnection 时，解析 netstat 输出
    $match = netstat -ano -p tcp | Select-String -Pattern ("^\s*TCP\s+\S+:{0}\s+\S+\s+\S+\s+(\d+)\s*$" -f $Port) | Select-Object -First 1
    if ($match) { return [int]$match.Matches[0].Groups[1].Value }
    return $null
}

# 本机局域网 IPv4 地址（演示时手机连同一 Wi-Fi 可访问）
function Get-LanIPv4 {
    try {
        return [System.Net.Dns]::GetHostAddresses([System.Net.Dns]::GetHostName()) |
            Where-Object { $_.AddressFamily -eq 'InterNetwork' } |
            ForEach-Object { $_.ToString() } |
            Where-Object { -not $_.StartsWith('127.') -and -not $_.StartsWith('169.254.') } |
            Select-Object -Unique
    } catch {
        return @()
    }
}

# pip 镜像：默认使用阿里云 PyPI 镜像（国内下载快），-NoMirror 时使用官方源 pypi.org
$script:PipMirror = 'https://mirrors.aliyun.com/pypi/simple/'
