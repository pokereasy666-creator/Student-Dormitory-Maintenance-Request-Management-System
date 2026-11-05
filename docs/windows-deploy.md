# Windows 部署与操作指南（Python 版）

本文介绍在 Windows 10 / 11 上从零开始运行学生宿舍报修管理系统（Python + Flask + MySQL 版），提供两种方式：

| 方式 | 适合场景 | 需要的软件 |
|---|---|---|
| **方式一：开发调试运行**（命令行 / PyCharm / VS Code） | 开发、调试、改代码 | Python 3.10+（推荐 3.12）、MySQL 8，可选 PyCharm |
| **方式二：脚本一键部署** | 答辩演示、部署到其他电脑 | Python 3.10+（推荐 3.12）、MySQL 8 |

两种方式都**不需要手动建库建表**：系统首次启动时自动创建 `dorm_repair_py` 数据库、数据表，并录入维修人员账号。

> 建议把项目放在**不含中文和空格的路径**下，例如 `D:\dorm-repair-py`。部署脚本已经支持中文和空格路径，但手动输入命令时会更方便。
>
> Python 版默认端口 **5000**、数据库 **dorm_repair_py**；Java 版是 8080 / dorm_repair。两个版本可以装在同一台电脑上同时运行，互不影响。

---

## 1. 安装运行环境

### 1.1 安装 Python 3.12

1. 打开 <https://www.python.org/downloads/windows/>，下载 **Python 3.12.x** 的 **Windows installer (64-bit)**。
   - 也可以用命令安装（Windows 10/11 自带 winget）：
     ```bat
     winget install Python.Python.3.12
     ```
   - 支持 3.10 ~ 3.13，推荐 3.12。
2. 运行安装包，**第一页务必勾选最下面的 "Add python.exe to PATH"**，然后点 **Install Now**。安装结束时如果出现 "Disable path length limit"，点一下即可（可选）。
3. **重新打开**一个命令提示符窗口（Win+R 输入 `cmd`），执行：
   ```bat
   python --version
   ```
   看到 `Python 3.12.x` 即成功。也可以用 `py --version` 检查（python.org 安装包自带的 py 启动器）。

> **输入 python 却打开了微软商店？** 这是 Windows 自带的"应用执行别名"。打开 设置 → 应用 → 高级应用设置 → **应用执行别名**，关闭 `python.exe` 和 `python3.exe` 两项，再重新打开命令提示符。
>
> 电脑上已经装过 Python 3.9 或更早的版本也没关系：部署脚本会依次在 py 启动器、PATH、`%LOCALAPPDATA%\Programs\Python` 等位置寻找 3.10 及以上的版本。

### 1.2 安装 MySQL 8

1. 打开 <https://dev.mysql.com/downloads/mysql/>，版本选 **8.4 LTS**（8.0 也可以），操作系统选 **Microsoft Windows**，下载 **MSI Installer**（页面提示登录时点 "No thanks, just start my download"）。
2. 运行安装包，安装类型选 **Typical**（典型）。安装结束后会打开 **MySQL Configurator** 配置向导（不同版本界面文字可能略有差异）：
   - 配置类型：**Development Computer**；端口：保持 **3306**；
   - 设置 **root 密码**：务必记住，后面要填到配置文件 `config.ini` 里；
   - Windows 服务：勾选 **Configure MySQL Server as a Windows Service** 和 **Start the MySQL Server at System Startup**（开机自动启动）；
   - 一路 Next，最后点 **Execute** 完成配置。
3. 确认 MySQL 已运行：Win+R 输入 `services.msc`，找到名称以 `MySQL` 开头的服务（如 `MySQL84`、`MySQL80`），状态应为"正在运行"。
4. （可选）验证 root 密码是否正确。MySQL 的 bin 目录默认不在 PATH 中，需要写完整路径：
   ```bat
   "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe" -uroot -p
   ```
   输入密码后出现 `mysql>` 提示符即正确，输入 `exit` 退出。

> MySQL 8 默认使用 `caching_sha2_password` 登录认证，Python 连接它需要 `cryptography` 组件——它已包含在依赖中（`PyMySQL[rsa]`），安装脚本会自动安装。
>
> 已有 MySQL 5.7 或 phpStudy / XAMPP 自带的 MySQL / MariaDB 一般也能直接使用（建库建表时显式指定 utf8mb4 字符集，中文不会乱码），但推荐 MySQL 8。

### 1.3 （可选）安装 PyCharm

到 <https://www.jetbrains.com/pycharm/download/> 下载安装。也可以使用 VS Code（安装 Python 扩展）。

---

## 2. 获取项目代码

二选一：

- **Git 克隆**（需安装 Git）：
  ```bat
  cd /d D:\
  git clone -b claude/python-flask-version https://github.com/pokereasy666-creator/Student-Dormitory-Maintenance-Request-Management-System.git dorm-repair-py
  ```
- **下载 ZIP**：在 GitHub 仓库页面切换到 `claude/python-flask-version` 分支，点 **Code → Download ZIP**。解压前先**右键 ZIP 文件 → 属性 → 勾选"解除锁定" → 确定**，否则双击 .bat 时 Windows 会弹安全警告。解压到 `D:\dorm-repair-py`。

---

## 3. 方式一：开发调试运行

### 3.1 命令行运行（最直观，推荐先用这种方式理解项目）

在命令提示符（cmd）中逐行执行：

```bat
cd /d D:\dorm-repair-py
```

```bat
py -3.12 -m venv .venv
```
创建虚拟环境：本项目专用的 Python 环境，安装的依赖不会影响电脑上的其他 Python 程序。没有 py 启动器时改用 `python -m venv .venv`。

```bat
.venv\Scripts\activate
```
激活虚拟环境，命令行前面会出现 `(.venv)`。

```bat
pip install -r requirements-dev.txt -i https://mirrors.aliyun.com/pypi/simple/
```
安装依赖（`requirements-dev.txt` 比 `requirements.txt` 多了测试工具 pytest），`-i` 指定阿里云镜像，国内下载更快。

```bat
copy config.example.ini config.ini
```

```bat
notepad config.ini
```
把 `[database]` 中的 `password = root` 改成你的 MySQL root 密码，保存。

```bat
python run.py --dev
```
开发模式启动：控制台显示 `Running on http://127.0.0.1:5000` 即成功，浏览器访问 <http://localhost:5000>。开发模式下修改 Python 代码会自动重启、控制台会打印执行的 SQL；修改 html 页面刷新浏览器即可看到效果。按 **Ctrl+C** 停止。

运行自动化测试（不需要 MySQL，使用临时 SQLite 数据库）：

```bat
pytest
```

> 在 PowerShell 而不是 cmd 中激活虚拟环境时，如提示"禁止运行脚本"，先执行一次 `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`，或直接改用 cmd。

### 3.2 PyCharm 运行

1. **打开项目**：File → Open，选择 `D:\dorm-repair-py` 文件夹 → Trust Project。
2. **配置解释器**：File → Settings → Project: dorm-repair-py → **Python Interpreter** → Add Interpreter → **Add Local Interpreter** → 选 **Virtualenv Environment**，Base python 选 Python 3.12，Location 设为项目下的 `.venv` → OK。（已经按 3.1 创建过 `.venv` 的，选 Existing 并指向 `.venv\Scripts\python.exe`。）
3. **安装依赖**：PyCharm 会提示安装 requirements 中的包，点 Install 即可；或在 PyCharm 底部 Terminal 中执行 3.1 中的 `pip install` 命令。想让 PyCharm 也走国内镜像，可先在命令行执行一次：
   ```bat
   pip config set global.index-url https://mirrors.aliyun.com/pypi/simple/
   ```
4. **配置文件**：把 `config.example.ini` 复制为 `config.ini`，修改数据库密码（不复制的话首次运行会自动生成一份默认的）。
5. **运行**：右键 `run.py` → Modify Run Configuration → **Parameters** 填 `--dev` → OK，然后点右上角绿色三角运行。控制台出现 `Running on http://127.0.0.1:5000` 后访问 <http://localhost:5000>。
6. **停止**：点运行窗口的红色方块。

### 3.3 VS Code 运行

安装 Python 扩展 → 打开项目文件夹 → Ctrl+Shift+P 输入 "Python: Select Interpreter" 选择 `.venv` → 打开 `run.py`，在终端执行 `python run.py --dev`。

---

## 4. 方式二：脚本一键部署（答辩演示 / 部署到其他电脑）

项目根目录下有 4 个脚本，双击即可运行：

| 脚本 | 作用 |
|---|---|
| `setup.bat` | 首次安装：查找 Python → 创建虚拟环境 `.venv` → 安装依赖 → 生成 `config.ini` → 初始化数据库 |
| `start.bat` | 启动系统（没安装过会自动先执行安装） |
| `stop.bat` | 停止系统 |
| `package.bat` | 打包成 zip，拷贝到其他电脑部署（含离线安装包） |

### 4.1 首次安装

双击 `setup.bat`。依赖默认从阿里云 PyPI 镜像下载（约 15MB，一两分钟）。最后一步会连接 MySQL：

- 如果提示"MySQL 用户名或密码错误"：用记事本打开项目根目录的 `config.ini`，修改密码（见 4.2）后重新双击 `setup.bat`；
- 看到"安装完成！双击 start.bat 启动系统"即成功。

国外网络或阿里云镜像不可用时，在命令提示符中运行：

```bat
setup.bat -NoMirror
```

### 4.2 修改配置

用记事本打开项目根目录下的 `config.ini`（首次运行时由 `config.example.ini` 自动复制生成）：

```ini
[database]
host = localhost
port = 3306
user = root
password = 改成你的MySQL密码
name = dorm_repair_py

[server]
port = 5000
upload_dir = uploads
```

保存后重新启动系统生效。值不需要加引号；密码中可以包含 `@ # %` 等特殊字符。

### 4.3 启动

双击 `start.bat`，脚本会：

1. 检查虚拟环境和依赖（未安装则自动执行 setup）→ 检查 5000 端口是否空闲 → 检查 MySQL 是否能连接（任何一项不满足都会用中文提示原因和解决办法）；
2. 启动系统，首次启动自动建库建表；窗口中会持续显示运行日志；
3. 出现绿色的"系统启动成功"后自动打开浏览器，并显示局域网访问地址：

```
==================================================
  系统启动成功！
  本机访问：  http://localhost:5000
  局域网访问：http://192.168.1.23:5000 （手机需连接同一 Wi-Fi）
  维修人员账号：W001 / W002 / W003，初始密码 123456
  演示学生账号：2023001，初始密码 123456
  ...
==================================================
```

注意事项：

- **使用期间不要关闭这个黑色窗口**，关闭窗口系统就会停止。
- 首次启动时如果弹出 **Windows 防火墙** 提示（程序名为 Python），想用手机访问就勾选"专用网络"并点"允许访问"；只在本机访问可以直接取消。
- 启动失败时，窗口会显示"启动失败：……"及原因（如 MySQL 密码错误），按提示修改 `config.ini` 后重新双击 `start.bat`。完整日志在 `logs\app.log`。
- 系统已经在运行时再次双击 `start.bat`，只会打开浏览器，不会重复启动。

### 4.4 停止

任选一种：关闭启动窗口；在启动窗口按 **Ctrl+C**（提示"终止批处理操作吗"时输入 Y）；或双击 **stop.bat**。stop.bat 只会停止本项目的进程，不会误关其他 Python 程序。

### 4.5 部署到另一台电脑（如答辩用的笔记本）

1. 在已经能正常运行的电脑上双击 `package.bat`，生成 `dist\dorm-repair-py-windows.zip`（约 10MB）。压缩包中包含程序、脚本、文档和 `wheels\`（依赖的离线安装包），**不包含**本机的 `config.ini`（密码）、`.venv`、日志和上传图片。
2. 目标电脑安装 **Python**（**与打包电脑相同的小版本**，如都是 3.12，才能离线安装依赖；版本不同时会自动改为联网安装）和 **MySQL 8**。
3. 拷贝 zip 到目标电脑，右键 → 属性 → 解除锁定，解压到如 `D:\dorm-repair-py`。
4. 双击 `setup.bat`（看到"离线安装完成"说明没有联网下载），按提示修改 `config.ini` 中的数据库密码。
5. 双击 `start.bat`。

> 不要把 `.venv` 文件夹直接拷贝到其他电脑或移动到别的目录使用——虚拟环境中记录了绝对路径，移动后会失效。如果移动了项目文件夹，`setup.bat` / `start.bat` 会自动检测并重建 `.venv`。

### 4.6 更新版本

用新版本的程序文件（`dorm_repair\`、`scripts\`、`run.py`、`requirements.txt`、`*.bat` 等）覆盖旧文件即可，**保留** `config.ini`、`uploads\`（上传的图片）、`logs\`、`instance\`、`.venv\`；数据库中的数据不受影响。如果 `requirements.txt` 有变化，再运行一次 `setup.bat`。更新前请先停止系统。

### 4.7 不使用脚本的手动启动（脚本无法运行时的备用方法）

```bat
cd /d D:\dorm-repair-py
```

```bat
.venv\Scripts\python.exe run.py
```

窗口出现 `系统已启动：http://localhost:5000` 后访问网站。没有 `.venv` 时先按 3.1 创建并安装依赖。

---

## 5. 系统操作步骤（演示流程）

下面是一次完整的业务闭环，答辩时可以照此演示。

**学生提交报修**

1. 打开 <http://localhost:5000>，点"学生注册"，填写学号、姓名、楼栋、宿舍号、手机号、密码 → 注册（或直接用演示账号 2023001 / 123456）。
2. 用学号登录，进入"首页公告"：可以看到公告和自己各状态工单数量。
3. 点"我要报修"：楼栋、宿舍号已自动带出；选择报修类型（水电/门窗/家具/卫生间），填写故障描述，可选上传图片（jpg/png/gif/webp，≤5MB）→ 提交报修。
4. 进入工单详情，状态为"待接单"。点"退出登录"。

**维修人员接单并完成**

5. 用 W001 / 123456 登录，进入"待接单"列表，可以看到刚才的工单（按提交时间先后排列）。
6. 点"详情"查看故障描述、图片和学生联系方式，点"接单"→ 状态变为"维修中"。
7. 在工单详情页填写维修备注（如"更换水龙头阀芯"）→"标记为已完成"。
8. "我的维修"中可以按"维修中 / 已完成"查看自己的全部维修记录。退出登录。

**学生查看并评价**

9. 学生重新登录 →"我的报修"，工单显示"已完成"和"去评价"按钮。
10. 进入详情，可以看到进度条、维修人员和维修备注；点选星级、填写评价 → 提交。每张工单只能评价一次。
11. 维修人员登录后，在"我的维修"中可以看到学生的星级评价。

**可以额外演示的设计要点**

- **角色权限隔离**：学生登录后，在地址栏直接输入 `http://localhost:5000/repair/pending`，返回"403 无权访问"。
- **防止重复接单**：开两个浏览器窗口（一个普通窗口、一个无痕窗口），分别登录 W001 和 W002，同时打开同一张待接单工单；先后点"接单"，后点的一方提示"手慢了，该工单已被其他维修人员接单"。
- **数据持久化**：停止系统后重新启动，之前的工单和评价都还在。

**清空测试数据**（答辩前恢复成初始状态）：在 MySQL 中执行下面的语句，然后重新启动系统，会重新建表并录入维修人员账号；同时可删除项目下的 `uploads` 文件夹。

```sql
DROP DATABASE dorm_repair_py;
```

---

## 6. 数据备份与恢复

数据包括：MySQL 中的 `dorm_repair_py` 数据库，以及项目下的 `uploads\` 图片文件夹。

**备份数据库**（路径中的版本号按实际安装修改）：

```bat
"C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqldump.exe" -uroot -p --default-character-set=utf8mb4 --databases dorm_repair_py > D:\backup\dorm_repair_py.sql
```

**恢复数据库**：

```bat
"C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe" -uroot -p --default-character-set=utf8mb4 < D:\backup\dorm_repair_py.sql
```

**备份图片**：直接复制 `uploads` 文件夹。

---

## 7. 常见问题

| 现象 | 原因与解决办法 |
|---|---|
| 双击 .bat 弹出"Windows 已保护你的电脑"或安全警告 | 文件来自网络下载。点"更多信息 → 仍要运行"；或在解压前右键 ZIP → 属性 → 解除锁定 |
| 提示"未找到 Python 3.10 或更高版本" | 未安装 Python，或安装时没勾选 "Add python.exe to PATH"。按 1.1 安装（可重新运行安装包选 Modify 补勾），然后重新打开窗口 |
| 输入 python 打开了微软商店 / 提示 "Python was not found" | 关闭"应用执行别名"中的 python.exe 和 python3.exe（见 1.1） |
| 安装依赖失败（超时、SSL 错误、找不到版本） | 检查网络后重新运行 `setup.bat`；阿里云镜像不可用时运行 `setup.bat -NoMirror`；也可以换成清华镜像：`pip config set global.index-url https://pypi.tuna.tsinghua.edu.cn/simple` 后按 3.1 手动安装 |
| 提示"无法连接到 MySQL" | MySQL 服务没启动：`services.msc` 中启动 MySQL 服务，或以管理员身份运行 `net start MySQL84`（服务名以实际为准） |
| 提示"MySQL 用户名或密码错误" | 修改 `config.ini` 中 `[database]` 的 user / password |
| 提示"缺少 cryptography 组件" | 依赖没装全，重新运行 `setup.bat` |
| 提示"当前 MySQL 账号没有创建数据库的权限" | 使用 root 账号；或让管理员先执行 `CREATE DATABASE dorm_repair_py DEFAULT CHARSET utf8mb4;` |
| 提示"端口 5000 已被占用" | PyCharm 中项目还在运行（先停止），或其他软件占用：修改 `config.ini` 中 `[server]` 的 `port = 5001` 后访问 `http://localhost:5001`。查看占用者：`netstat -ano \| findstr :5000`，结束进程：`taskkill /PID 进程号 /F` |
| 移动或复制项目文件夹后无法启动 | 虚拟环境不能移动。重新运行 `setup.bat`（start.bat 也会自动检测并重建 `.venv`） |
| 运行脚本提示"禁止运行脚本" | 学校机房等电脑的组策略禁止了 PowerShell 脚本。改用 3.1 的命令手动安装，再按 4.7 手动启动 |
| 点击黑窗口后日志不滚动了 | 窗口进入了"选择"状态（标题栏出现"选择"），按 Esc 恢复。系统本身不受影响，仍正常运行 |
| 手机无法访问 | 手机与电脑必须连同一个 Wi-Fi；使用启动窗口显示的"局域网访问"地址；在 Windows 防火墙中允许 Python 在专用网络通信；校园网可能禁止设备互访，可改用手机热点 |
| 页面提示"页面已过期或请求来源不合法" | 表单的 CSRF 校验失败（如登录状态已过期后再提交）。刷新页面重新操作即可 |
| MySQL 命令行中中文显示为问号 | 登录 MySQL 时加 `--default-character-set=utf8mb4` |
| 上传图片提示"图片大小不能超过 5MB" | 系统限制单张图片 5MB，请压缩后再上传 |
