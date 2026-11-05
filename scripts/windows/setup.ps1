# =====================================================================
# 安装 / 检查运行环境（由 setup.bat 调用；start.bat 发现未安装时也会自动调用）
#   1. 查找 Python 3.10+
#   2. 创建虚拟环境 .venv（项目被移动后旧的 .venv 会失效，自动重建）
#   3. 安装依赖：有 wheels 文件夹（package.bat 打的离线包）先离线安装，否则联网安装
#   4. 生成 config.ini，连接 MySQL 并初始化数据库（建库、建表、预置账号）
# 参数：
#   -NoMirror    不使用阿里云 PyPI 镜像，改用官方源（国外网络时使用）
#   -SkipDbInit  只安装依赖，不初始化数据库（start.bat 调用时使用）
# =====================================================================
param(
    [switch]$NoMirror,
    [switch]$SkipDbInit
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'common.ps1')
Initialize-Console

$appDir = $script:AppDir
Set-Location -LiteralPath $appDir

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "   学生宿舍报修管理系统（Python 版）- 安装环境" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "项目目录：$appDir"

# ---------- 1. Python ----------
Write-Step "检查 Python（需要 3.$($script:MinPythonMinor) 及以上版本）"
$python = Find-Python
if (-not $python) {
    Write-Fail "未找到 Python 3.$($script:MinPythonMinor) 或更高版本。"
    Write-Hint "请到 https://www.python.org/downloads/windows/ 下载 Python 3.12 安装包，"
    Write-Hint "安装第一页务必勾选 'Add python.exe to PATH'，安装完成后重新打开本窗口再试。"
    Write-Hint "如果在命令行输入 python 会打开微软商店：设置 -> 应用 -> 高级应用设置 -> 应用执行别名，"
    Write-Hint "关闭 python.exe 和 python3.exe 两项。详见 docs\windows-deploy.md 第 1 节。"
    exit 1
}
Write-Ok "Python $($python.Version)  ->  $($python.Exe)"

# ---------- 2. 虚拟环境 ----------
Write-Step "准备虚拟环境 .venv（本项目专用的 Python 环境，不影响电脑上的其他 Python 程序）"
$venvDir = Join-Path $appDir '.venv'
$venvPython = Get-VenvPython $appDir
if (Test-Path -LiteralPath $venvPython) {
    $venvInfo = Get-PythonInfo $venvPython
    if (-not $venvInfo -or $venvInfo.Minor -lt $script:MinPythonMinor) {
        Write-Hint "已有的虚拟环境无法使用（项目文件夹被移动过，或原来的 Python 已卸载），正在重新创建……"
        Remove-Item -LiteralPath $venvDir -Recurse -Force
    }
}
if (Test-Path -LiteralPath $venvPython) {
    Write-Ok "虚拟环境已存在"
} else {
    $prefix = @()
    if ($python.PrefixArgs) { $prefix = @($python.PrefixArgs) }
    & $python.Command @prefix -m venv $venvDir
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $venvPython)) {
        Write-Fail "创建虚拟环境失败，请查看上方的错误信息。"
        exit 1
    }
    Write-Ok "虚拟环境已创建：$venvDir"
}

# ---------- 3. 安装依赖 ----------
Write-Step "安装依赖包（requirements.txt）"
$requirements = Join-Path $appDir 'requirements.txt'
$wheelDir = Join-Path $appDir 'wheels'
$installed = $false
if (Test-Path -Path (Join-Path $wheelDir '*.whl')) {
    Write-Hint "检测到 wheels 文件夹（离线安装包），先尝试离线安装……"
    & $venvPython -m pip install --disable-pip-version-check --no-index --find-links $wheelDir -r $requirements
    if ($LASTEXITCODE -eq 0) {
        $installed = $true
        Write-Ok "离线安装完成"
    } else {
        Write-Hint "离线安装失败（通常是本机 Python 版本与打包电脑不同），改为联网安装……"
    }
}
if (-not $installed) {
    $indexArgs = @()
    if (-not $NoMirror) {
        $indexArgs = @('-i', $script:PipMirror)
        Write-Hint "从阿里云 PyPI 镜像下载（国外网络请使用 setup.bat -NoMirror）……"
    }
    & $venvPython -m pip install --disable-pip-version-check @indexArgs -r $requirements
    if ($LASTEXITCODE -ne 0) {
        Write-Fail "依赖安装失败，请查看上方的错误信息。"
        Write-Hint "常见原因：网络不通或镜像暂时不可用。检查网络后重新运行 setup.bat；"
        Write-Hint "也可以换用官方源：在命令提示符中运行 setup.bat -NoMirror"
        exit 1
    }
    Write-Ok "依赖安装完成"
}

# ---------- 4. 配置文件与数据库 ----------
Write-Step "检查配置文件和数据库"
$config = Get-AppConfig $appDir
Write-Ok "配置文件：$($config.Path)"
if ($SkipDbInit) { exit 0 }

if (-not (Test-TcpPort $config.DbHost $config.DbPort 3000)) {
    Show-MySqlHint $config.DbHost $config.DbPort
    Write-Hint "Python 依赖已经安装好。启动 MySQL 后，直接双击 start.bat 即可（首次启动会自动建库建表）。"
    exit 1
}
& $venvPython (Join-Path $appDir 'run.py') --init-db
if ($LASTEXITCODE -ne 0) {
    Write-Fail "数据库初始化失败，原因见上方提示。"
    Write-Hint "用记事本修改 $($config.Path) 后，重新运行 setup.bat 或直接运行 start.bat。"
    exit 1
}
Write-Ok "数据库已就绪"

Write-Host ""
Write-Host "==================================================" -ForegroundColor Green
Write-Host "  安装完成！双击 start.bat 启动系统。" -ForegroundColor Green
Write-Host "==================================================" -ForegroundColor Green
