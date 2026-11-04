# =====================================================================
# 打包脚本（由 build.bat 调用）：编译项目，生成可直接部署的文件夹和压缩包
#   输出：dist\dorm-repair\              部署目录（可直接运行 start.bat）
#         dist\dorm-repair-windows.zip   压缩包（拷贝到其他电脑解压即可部署）
# 参数：
#   -NoMirror   不使用阿里云镜像，直接从 Maven 中央仓库下载依赖（国外网络时使用）
#   -RunTests   打包前运行自动化测试（使用内存数据库，不需要 MySQL）
#   -NoZip      不生成 zip 压缩包
# 不需要单独安装 Maven：使用项目自带的 Maven Wrapper（mvnw.cmd），首次运行会自动下载 Maven。
# =====================================================================
param(
    [switch]$NoMirror,
    [switch]$RunTests,
    [switch]$NoZip
)

$ErrorActionPreference = 'Stop'
$packageDir = Join-Path $PSScriptRoot 'package'
. (Join-Path (Join-Path $packageDir 'scripts') 'common.ps1')
Initialize-Console

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location -LiteralPath $repoRoot

Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "        学生宿舍报修管理系统 - 打包" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "项目目录：$repoRoot"

# ---------- 1. 检查 JDK ----------
Write-Step "检查 JDK（打包需要 JDK 17 及以上，仅有 JRE 不行）"
$java = Find-Java -MinMajor 17 -RequireJdk
if (-not $java) {
    Write-Fail "未找到 JDK 17 或更高版本。"
    Write-Hint "请安装 JDK 17（推荐 Eclipse Temurin 17），安装时勾选 'Set JAVA_HOME' 和 'Add to PATH'。"
    Write-Hint "安装完成后重新打开窗口再运行 build.bat。详见 docs/windows-deploy.md 第 1 节。"
    exit 1
}
Write-Ok "JDK $($java.Version)  ->  $($java.Home)"
# Maven 通过 JAVA_HOME 找 JDK：这里统一指向检测到的 JDK，避免系统中 JAVA_HOME 指向旧版本 JDK 8 导致编译失败
$env:JAVA_HOME = $java.Home

# ---------- 2. Maven 编译打包 ----------
if (Test-IsWindows) { $mvnw = Join-Path $repoRoot 'mvnw.cmd' } else { $mvnw = Join-Path $repoRoot 'mvnw' }
$mvnArgs = @('-B', 'clean', 'package')
if (-not $RunTests) { $mvnArgs += '-DskipTests' }
if (-not $NoMirror) {
    # 国内网络：Maven 本体和项目依赖都从阿里云镜像下载
    $env:MVNW_REPOURL = 'https://maven.aliyun.com/repository/public'
    $mvnArgs += @('-s', (Join-Path $PSScriptRoot 'maven-settings-aliyun.xml'))
    Write-Step "开始编译打包（使用阿里云 Maven 镜像；首次运行需要下载依赖，约 100MB，请耐心等待）"
} else {
    Write-Step "开始编译打包（使用 Maven 中央仓库；首次运行需要下载依赖，约 100MB，请耐心等待）"
}
& $mvnw @mvnArgs
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Fail "编译打包失败（Maven 退出码 $LASTEXITCODE），请查看上方的 ERROR 信息。"
    Write-Hint "常见原因："
    Write-Hint "  1. 依赖下载失败：检查网络后重试；国外网络请使用 build.bat -NoMirror"
    Write-Hint "  2. 下载中断导致文件损坏：删除 %USERPROFILE%\.m2\repository 中报错的目录后重试"
    Write-Hint "  3. 测试失败（仅 -RunTests 时）：查看 target\surefire-reports 中的报告"
    exit 1
}

$jar = Get-ChildItem -Path (Join-Path $repoRoot 'target') -Filter 'dorm-repair-*.jar' | Select-Object -First 1
if (-not $jar) {
    Write-Fail "没有在 target 目录找到打包好的 jar 文件。"
    exit 1
}
Write-Ok "打包完成：$($jar.Name)（$([math]::Round($jar.Length / 1MB, 1)) MB）"

# ---------- 3. 组装部署目录 ----------
$distRoot = Join-Path $repoRoot 'dist'
$appDir   = Join-Path $distRoot 'dorm-repair'
Write-Step "生成部署目录：$appDir"
New-Item -ItemType Directory -Force -Path (Join-Path $appDir 'scripts') | Out-Null

try {
    Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $appDir 'dorm-repair.jar') -Force
} catch {
    Write-Fail "无法覆盖 dist\dorm-repair\dorm-repair.jar，系统可能正在运行。请先运行 dist\dorm-repair\stop.bat 再重新打包。"
    exit 1
}
foreach ($name in @('start.bat', 'stop.bat', 'README.txt')) {
    Copy-Item -LiteralPath (Join-Path $packageDir $name) -Destination (Join-Path $appDir $name) -Force
}
Copy-Item -Path (Join-Path (Join-Path $packageDir 'scripts') '*.ps1') -Destination (Join-Path $appDir 'scripts') -Force

# 配置文件：已存在则保留（不覆盖你改过的数据库密码）；uploads、logs 目录也不会被删除
$appConfig = Join-Path $appDir 'application.properties'
if (Test-Path -LiteralPath $appConfig) {
    Write-Hint "已保留原有配置文件 application.properties（新版模板见 deploy\windows\package\application.properties）"
} else {
    Copy-Item -LiteralPath (Join-Path $packageDir 'application.properties') -Destination $appConfig
}
Write-Ok "部署目录已生成"

# ---------- 4. 生成压缩包（用默认配置，不包含本机的密码、日志和上传的图片） ----------
if (-not $NoZip) {
    $zipPath = Join-Path $distRoot 'dorm-repair-windows.zip'
    $staging = Join-Path $distRoot '_staging'
    $stagingApp = Join-Path $staging 'dorm-repair'
    Remove-Item -LiteralPath $staging -Recurse -Force -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path (Join-Path $stagingApp 'scripts') | Out-Null
    foreach ($name in @('dorm-repair.jar', 'start.bat', 'stop.bat', 'README.txt')) {
        Copy-Item -LiteralPath (Join-Path $appDir $name) -Destination (Join-Path $stagingApp $name)
    }
    Copy-Item -Path (Join-Path (Join-Path $appDir 'scripts') '*.ps1') -Destination (Join-Path $stagingApp 'scripts')
    Copy-Item -LiteralPath (Join-Path $packageDir 'application.properties') -Destination (Join-Path $stagingApp 'application.properties')
    Remove-Item -LiteralPath $zipPath -Force -ErrorAction SilentlyContinue
    Compress-Archive -Path $stagingApp -DestinationPath $zipPath
    Remove-Item -LiteralPath $staging -Recurse -Force
    Write-Ok "压缩包：$zipPath"
}

Write-Host ""
Write-Host "==================================================" -ForegroundColor Green
Write-Host "  打包成功！接下来：" -ForegroundColor Green
Write-Host "  1. 用记事本打开 dist\dorm-repair\application.properties，" -ForegroundColor Green
Write-Host "     把 DB_PASSWORD 改成你的 MySQL root 密码" -ForegroundColor Green
Write-Host "  2. 双击 dist\dorm-repair\start.bat 启动系统" -ForegroundColor Green
Write-Host "  部署到其他电脑：拷贝 dist\dorm-repair-windows.zip，解压后同样操作" -ForegroundColor Green
Write-Host "==================================================" -ForegroundColor Green
