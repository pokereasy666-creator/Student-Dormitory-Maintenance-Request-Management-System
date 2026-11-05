# =====================================================================
# 打包脚本（由 package.bat 调用）：生成可拷贝到其他电脑部署的压缩包
#   输出：dist\dorm-repair-py-windows.zip
#   内容：程序源码 + 启动脚本 + 文档 + wheels\（依赖的离线安装包）
#   不包含：.venv、config.ini（含本机密码）、logs、uploads、测试代码
# 目标电脑只需安装 Python 和 MySQL；Python 小版本与本机相同（如都是 3.12）时可离线安装依赖，
# 不同时 setup.bat 会自动改为联网安装。
# 参数：
#   -NoMirror   下载离线包时不使用阿里云镜像
#   -NoWheels   不下载离线安装包（压缩包更小，目标电脑需联网安装依赖）
# =====================================================================
param(
    [switch]$NoMirror,
    [switch]$NoWheels
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'common.ps1')
Initialize-Console

$appDir   = $script:AppDir
$distRoot = Join-Path $appDir 'dist'
$staging  = Join-Path $distRoot '_staging'
$pkgDir   = Join-Path $staging 'dorm-repair-py'
$zipPath  = Join-Path $distRoot 'dorm-repair-py-windows.zip'
Set-Location -LiteralPath $appDir

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "   学生宿舍报修管理系统（Python 版）- 打包" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

# ---------- 1. 复制项目文件 ----------
Write-Step "复制程序文件"
Remove-Item -LiteralPath $staging -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $pkgDir | Out-Null
$items = @('dorm_repair', 'scripts', 'docs', 'run.py', 'requirements.txt', 'config.example.ini',
           'README.md', 'setup.bat', 'start.bat', 'stop.bat')
foreach ($item in $items) {
    Copy-Item -LiteralPath (Join-Path $appDir $item) -Destination $pkgDir -Recurse -Force
}
# 删除 Python 运行时生成的缓存文件
Get-ChildItem -LiteralPath $pkgDir -Recurse -Directory -Filter '__pycache__' | Remove-Item -Recurse -Force
Write-Ok "程序文件已复制"

# ---------- 2. 下载依赖的离线安装包 ----------
if (-not $NoWheels) {
    Write-Step "下载依赖的离线安装包（wheels）"
    $venvPython = Get-VenvPython $appDir
    if (Test-VenvReady $appDir) {
        $pyCommand = $venvPython; $prefix = @()
        $pyInfo = Get-PythonInfo $venvPython
    } else {
        $pyInfo = Find-Python
        if (-not $pyInfo) {
            Write-Fail "未找到 Python 3.$($script:MinPythonMinor)+，无法下载离线安装包。可先运行 setup.bat，或使用 package.bat -NoWheels。"
            exit 1
        }
        $pyCommand = $pyInfo.Command; $prefix = @()
        if ($pyInfo.PrefixArgs) { $prefix = @($pyInfo.PrefixArgs) }
    }
    $indexArgs = @()
    if (-not $NoMirror) { $indexArgs = @('-i', $script:PipMirror) }
    $wheelDir = Join-Path $pkgDir 'wheels'
    # --only-binary=:all:：只下载已编译好的 wheel 包，目标电脑不需要编译环境
    & $pyCommand @prefix -m pip download --disable-pip-version-check --only-binary=:all: `
        -d $wheelDir -r (Join-Path $appDir 'requirements.txt') @indexArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Fail "下载离线安装包失败，请检查网络后重试；或使用 package.bat -NoWheels 打包（目标电脑需联网安装依赖）。"
        exit 1
    }
    $pyMinor = "3.$($pyInfo.Minor)"
    Set-Content -LiteralPath (Join-Path $wheelDir 'PYTHON_VERSION.txt') -Encoding ASCII `
        -Value "These wheels were downloaded with Python $($pyInfo.Version). Offline install works on Python $pyMinor."
    $count = @(Get-ChildItem -LiteralPath $wheelDir -Filter '*.whl').Count
    Write-Ok "已下载 $count 个离线安装包（适用于 Python $pyMinor）"
}

# ---------- 3. 压缩 ----------
Write-Step "生成压缩包"
New-Item -ItemType Directory -Force -Path $distRoot | Out-Null
Remove-Item -LiteralPath $zipPath -Force -ErrorAction SilentlyContinue
Compress-Archive -Path $pkgDir -DestinationPath $zipPath
Remove-Item -LiteralPath $staging -Recurse -Force
$sizeMb = [math]::Round((Get-Item -LiteralPath $zipPath).Length / 1MB, 1)
Write-Ok "压缩包：$zipPath（$sizeMb MB）"

Write-Host ""
Write-Host "==================================================" -ForegroundColor Green
Write-Host "  打包成功！部署到其他电脑：" -ForegroundColor Green
Write-Host "  1. 目标电脑安装 Python（建议与本机相同的小版本，可离线安装依赖）和 MySQL 8" -ForegroundColor Green
Write-Host "  2. 拷贝压缩包，右键 -> 属性 -> 解除锁定，然后解压" -ForegroundColor Green
Write-Host "  3. 双击 setup.bat，按提示修改 config.ini 中的数据库密码" -ForegroundColor Green
Write-Host "  4. 双击 start.bat 启动系统" -ForegroundColor Green
Write-Host "==================================================" -ForegroundColor Green
