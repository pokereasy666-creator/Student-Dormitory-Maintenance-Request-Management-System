# =====================================================================
# 公共函数：start.ps1 / stop.ps1 / build.ps1 共用
# 兼容 Windows 自带的 PowerShell 5.1（不使用 ?? 、三元运算符等 PowerShell 7 新语法）
# 注意：本文件必须保存为 "UTF-8 with BOM"，否则 PowerShell 5.1 会把中文读成乱码。
# =====================================================================

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

# 可执行文件名：Windows 下加 .exe
function Get-ExeName([string]$Name) {
    if (Test-IsWindows) { return "$Name.exe" }
    return $Name
}

# ---------------------------------------------------------------------
# Java 检测
# ---------------------------------------------------------------------

# 运行 "java -XshowSettings:properties -version"，解析出版本号和 java.home（JDK 安装目录）
# 返回 $null 表示该 java 无法运行
function Get-JavaInfo([string]$JavaExe) {
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $JavaExe
    # -Dsun.stderr.encoding / -Dstderr.encoding：让 Java 用 UTF-8 输出（Java 17 / 19+ 的写法各一个），
    # 否则中文 Windows 上按 GBK 输出，JDK 位于中文用户名目录（如 C:\Users\张三\.jdks）时路径会解码成乱码
    $psi.Arguments = '-Dsun.stderr.encoding=UTF-8 -Dstderr.encoding=UTF-8 -XshowSettings:properties -version'
    $psi.UseShellExecute = $false
    $psi.RedirectStandardError = $true
    $psi.RedirectStandardOutput = $true
    $psi.StandardErrorEncoding = [System.Text.Encoding]::UTF8
    $psi.StandardOutputEncoding = [System.Text.Encoding]::UTF8
    $psi.CreateNoWindow = $true
    try {
        $proc = [System.Diagnostics.Process]::Start($psi)
    } catch {
        return $null
    }
    $stdoutTask = $proc.StandardOutput.ReadToEndAsync()
    $text = $proc.StandardError.ReadToEnd()
    $proc.WaitForExit()
    $text = $text + $stdoutTask.Result

    if ($text -notmatch '(?m)^\s*java\.version\s*=\s*(\S+)') { return $null }
    $version = $Matches[1]
    $javaHome = $null
    if ($text -match '(?m)^\s*java\.home\s*=\s*(.+?)\s*$') { $javaHome = $Matches[1] }

    # "17.0.12" -> 17；老版本 "1.8.0_391" -> 8
    $major = 0
    if ($version -match '^1\.(\d+)') { $major = [int]$Matches[1] }
    elseif ($version -match '^(\d+)') { $major = [int]$Matches[1] }

    return [pscustomobject]@{ Exe = $JavaExe; Version = $version; Major = $major; Home = $javaHome }
}

# 列出本机可能的 java 可执行文件：JAVA_HOME → PATH → Windows 常见安装目录（含 IDEA 下载的 JDK）
function Get-JavaCandidates {
    $exe = Get-ExeName 'java'
    $list = New-Object System.Collections.Generic.List[string]
    if ($env:JAVA_HOME) { $list.Add((Join-Path (Join-Path $env:JAVA_HOME 'bin') $exe)) }
    $cmd = Get-Command java -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($cmd) { $list.Add($cmd.Path) }
    if (Test-IsWindows) {
        $roots = @(
            (Join-Path $env:ProgramFiles 'Eclipse Adoptium'),
            (Join-Path $env:ProgramFiles 'Java'),
            (Join-Path $env:ProgramFiles 'Microsoft'),
            (Join-Path $env:ProgramFiles 'Zulu'),
            (Join-Path $env:ProgramFiles 'BellSoft'),
            (Join-Path $env:ProgramFiles 'Amazon Corretto'),
            (Join-Path $env:USERPROFILE '.jdks')            # IntelliJ IDEA 下载的 JDK
        )
        foreach ($root in $roots) {
            if (-not (Test-Path -LiteralPath $root)) { continue }
            Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue | ForEach-Object {
                $list.Add((Join-Path (Join-Path $_.FullName 'bin') $exe))
            }
        }
    }
    # 去重并只保留真实存在的文件
    $seen = @{}
    $result = @()
    foreach ($path in $list) {
        if ($seen.ContainsKey($path)) { continue }
        $seen[$path] = $true
        if (Test-Path -LiteralPath $path) { $result += $path }
    }
    return $result
}

# 查找版本 >= MinMajor 的 Java；RequireJdk 表示必须是 JDK（带 javac 编译器，打包时需要）
# 找不到时打印检测到的所有版本，便于排查，并返回 $null
function Find-Java([int]$MinMajor = 17, [switch]$RequireJdk) {
    $checked = @()
    foreach ($exe in Get-JavaCandidates) {
        $info = Get-JavaInfo $exe
        if (-not $info) { continue }
        $checked += $info
        if ($info.Major -lt $MinMajor) { continue }
        if ($RequireJdk) {
            if (-not $info.Home) { continue }
            $javac = Join-Path (Join-Path $info.Home 'bin') (Get-ExeName 'javac')
            if (-not (Test-Path -LiteralPath $javac)) { continue }
        }
        return $info
    }
    if ($checked.Count -gt 0) {
        Write-Hint "检测到以下 Java，但都不满足要求（需要 $MinMajor 及以上版本$(if ($RequireJdk) { '的 JDK' })）："
        foreach ($i in $checked) { Write-Hint "  - Java $($i.Version)  $($i.Exe)" }
    }
    return $null
}

# ---------------------------------------------------------------------
# 配置文件 / 网络 / 进程
# ---------------------------------------------------------------------

# 读取 key=value 格式的 .properties 文件（忽略 # 开头的注释行），返回哈希表
function Read-Properties([string]$Path) {
    $map = @{}
    if (-not (Test-Path -LiteralPath $Path)) { return $map }
    foreach ($line in [System.IO.File]::ReadAllLines($Path)) {
        $t = $line.Trim().TrimStart([char]0xFEFF)
        if ($t -eq '' -or $t.StartsWith('#') -or $t.StartsWith('!')) { continue }
        $idx = $t.IndexOf('=')
        if ($idx -lt 1) { continue }
        $map[$t.Substring(0, $idx).Trim()] = $t.Substring($idx + 1).Trim()
    }
    return $map
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

# 获取进程的完整命令行（用于确认某个 java 进程确实是本系统，避免误杀其他程序）
function Get-ProcessCommandLine([int]$ProcessId) {
    if (Test-IsWindows) {
        try { return (Get-CimInstance Win32_Process -Filter "ProcessId=$ProcessId" -ErrorAction Stop).CommandLine } catch { }
        try { return (Get-WmiObject Win32_Process -Filter "ProcessId=$ProcessId" -ErrorAction Stop).CommandLine } catch { }
        return $null
    }
    try { return (Get-Process -Id $ProcessId -ErrorAction Stop).CommandLine } catch { return $null }
}

# 判断进程是否是本系统（java -jar dorm-repair.jar）
function Test-IsAppProcess([int]$ProcessId) {
    $cmdLine = Get-ProcessCommandLine $ProcessId
    return [bool]($cmdLine -and $cmdLine -match 'dorm-repair[^\\/]*\.jar')
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
