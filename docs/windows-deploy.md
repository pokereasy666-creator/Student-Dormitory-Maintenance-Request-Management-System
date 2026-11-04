# Windows 部署与操作指南

本文介绍在 Windows 10 / 11 上从零开始运行学生宿舍报修管理系统，提供两种方式：

| 方式 | 适合场景 | 需要的软件 |
|---|---|---|
| **方式一：IDEA 直接运行** | 开发、调试、改代码 | JDK 17、MySQL、IntelliJ IDEA |
| **方式二：脚本一键打包部署** | 答辩演示、部署到其他电脑 | 打包电脑：JDK 17；运行电脑：JDK 17 + MySQL |

两种方式都**不需要手动建库建表**：系统首次启动时自动创建 `dorm_repair` 数据库、数据表，并录入维修人员账号。

> 建议把项目放在**不含中文和空格的路径**下，例如 `D:\dorm-repair`，可以避免很多奇怪的问题。

---

## 1. 安装运行环境

### 1.1 安装 JDK 17

1. 打开 <https://adoptium.net/zh-CN/temurin/releases/?version=17>，操作系统选 **Windows**、架构选 **x64**、类型选 **JDK**，下载 `.msi` 安装包。
   - 国外网站下载慢时，可以到清华大学开源软件镜像站（mirrors.tuna.tsinghua.edu.cn）的 Adoptium 目录下载同版本安装包。
   - 也可以用命令安装（Windows 10/11 自带 winget）：
     ```bat
     winget install EclipseAdoptium.Temurin.17.JDK
     ```
2. 安装时在"自定义安装"页面，点击 **Set JAVA_HOME variable** 前面的图标，选择"将安装在本地硬盘上"（这一项默认不安装）；**Add to PATH** 保持默认安装。
3. 安装完成后，**重新打开**一个命令提示符窗口（Win+R 输入 `cmd`），执行：
   ```bat
   java -version
   ```
   看到 `openjdk version "17.x.x"` 即成功。

> 已经装过 JDK 8 也没关系：项目自带的脚本会自动在 `JAVA_HOME`、`PATH`、`C:\Program Files\...` 和 IDEA 下载的 JDK（`%USERPROFILE%\.jdks`）中寻找 17 及以上版本。

### 1.2 安装 MySQL 8

1. 打开 <https://dev.mysql.com/downloads/mysql/>，版本选 **8.4 LTS**（8.0 也可以），操作系统选 **Microsoft Windows**，下载 **MSI Installer**（页面提示登录时点 "No thanks, just start my download"）。
2. 运行安装包，安装类型选 **Typical**（典型）。安装结束后会打开 **MySQL Configurator** 配置向导（不同版本界面文字可能略有差异）：
   - 配置类型：**Development Computer**；端口：保持 **3306**；
   - 设置 **root 密码**：务必记住，后面要填到配置文件里；
   - Windows 服务：勾选 **Configure MySQL Server as a Windows Service** 和 **Start the MySQL Server at System Startup**（开机自动启动）；
   - 一路 Next，最后点 **Execute** 完成配置。
3. 确认 MySQL 已运行：Win+R 输入 `services.msc`，找到名称以 `MySQL` 开头的服务（如 `MySQL84`、`MySQL80`），状态应为"正在运行"。
4. （可选）验证 root 密码是否正确。MySQL 的 bin 目录默认不在 PATH 中，需要写完整路径：
   ```bat
   "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe" -uroot -p
   ```
   输入密码后出现 `mysql>` 提示符即正确，输入 `exit` 退出。

> 已经装了 MySQL 5.7 或 phpStudy/XAMPP 自带的 MySQL/MariaDB，一般也能直接用（数据表显式使用 utf8mb4 字符集，中文不会乱码），但推荐使用 MySQL 8。

### 1.3 （方式一需要）安装 IntelliJ IDEA

到 <https://www.jetbrains.com/idea/download/> 下载 Community（免费）或 Ultimate 版并安装。

---

## 2. 获取项目代码

二选一：

- **Git 克隆**（需安装 Git）：
  ```bat
  cd /d D:\
  git clone -b claude/youthful-cerf-yhmk2y https://github.com/pokereasy666-creator/Student-Dormitory-Maintenance-Request-Management-System.git dorm-repair
  ```
  代码合并到 main 分支后，去掉 `-b claude/youthful-cerf-yhmk2y` 即可。
- **下载 ZIP**：在 GitHub 仓库页面切换到对应分支，点 **Code → Download ZIP**。解压前先**右键 ZIP 文件 → 属性 → 勾选"解除锁定" → 确定**，否则双击 .bat 时 Windows 会弹安全警告。解压到 `D:\dorm-repair`。

---

## 3. 方式一：在 IDEA 中运行（开发调试）

1. **打开项目**：IDEA → File → Open，选择 `D:\dorm-repair` 文件夹 → 弹窗选 **Trust Project**。右下角会显示 Maven 正在导入依赖，首次需要几分钟。
2. **（国内网络建议）配置阿里云镜像**：File → Settings → Build, Execution, Deployment → Build Tools → **Maven**，勾选 **User settings file** 右侧的 **Override**，选择项目中的 `deploy\windows\maven-settings-aliyun.xml`，点 OK。然后在右侧 Maven 面板点刷新按钮（Reload All Maven Projects）。
3. **设置 JDK**：File → Project Structure → Project → **SDK** 选 17。如果下拉框里没有，可以点 **Add SDK → Download JDK**，选 Eclipse Temurin 17 让 IDEA 自动下载。
4. **设置文件编码**（防止中文乱码）：File → Settings → Editor → **File Encodings**，Global Encoding、Project Encoding、Default encoding for properties files 都选 **UTF-8**。
5. **修改数据库密码**：打开 `src/main/resources/application.yml`，找到
   ```yaml
   password: ${DB_PASSWORD:root}
   ```
   把冒号后面的 `root` 改成你的 MySQL root 密码。
   - 不想把密码写进代码，也可以不改文件：Run → Edit Configurations → 选中 DormRepairApplication → Environment variables 填 `DB_PASSWORD=你的密码`。
6. **运行**：打开 `src/main/java/com/dorm/repair/DormRepairApplication.java`，点击 `main` 方法左边的绿色三角 → Run。控制台出现 `Started DormRepairApplication` 即启动成功。
7. 浏览器访问 <http://localhost:8080>。
8. **停止**：点击 IDEA 运行窗口的红色方块。

> 方式一运行时会在控制台打印执行的 SQL，方便调试。修改 html 页面后刷新浏览器即可看到效果；修改 Java 代码需要重新运行。

---

## 4. 方式二：脚本一键打包部署（答辩演示 / 部署到其他电脑）

脚本位于 `deploy\windows\`，**不需要单独安装 Maven**（项目自带 Maven Wrapper，首次运行会自动下载）。

### 4.1 打包

双击 `deploy\windows\build.bat`，脚本会依次：

1. 检查 JDK 17（打包需要完整 JDK，仅有 JRE 不行）；
2. 通过阿里云镜像下载 Maven 和项目依赖（首次约 100MB，之后有缓存）并编译打包；
3. 生成部署目录 `dist\dorm-repair\` 和压缩包 `dist\dorm-repair-windows.zip`。

看到"打包成功"即完成。可选参数（在命令提示符中运行）：

```bat
deploy\windows\build.bat -NoMirror
```
不使用阿里云镜像，改用 Maven 中央仓库（在国外或镜像不可用时使用）。

```bat
deploy\windows\build.bat -RunTests
```
打包前运行 11 个自动化测试（使用内存数据库，不需要 MySQL）。

生成的部署目录结构：

```
dist\dorm-repair\
├── dorm-repair.jar           系统程序（已包含所有依赖和页面）
├── application.properties    配置文件：数据库账号密码、端口
├── start.bat                 启动
├── stop.bat                  停止
├── README.txt                简要说明
├── scripts\                  启动/停止脚本的具体逻辑（PowerShell）
├── logs\                     运行后生成：app.log 运行日志
└── uploads\                  运行后生成：学生上传的报修图片
```

### 4.2 修改配置

用记事本打开 `dist\dorm-repair\application.properties`，至少修改数据库密码：

```properties
DB_HOST=localhost
DB_PORT=3306
DB_USERNAME=root
DB_PASSWORD=改成你的MySQL密码
server.port=8080
```

保存即可。值不要加引号、不要包含中文。

### 4.3 启动

双击 `dist\dorm-repair\start.bat`，脚本会：

1. 检查 Java 17+ → 检查 8080 端口是否空闲 → 检查 MySQL 是否能连接（任何一项不满足都会用中文提示原因和解决办法）；
2. 启动系统，首次启动自动建库建表；窗口中会持续显示运行日志；
3. 出现绿色的"系统启动成功"后自动打开浏览器，并显示局域网访问地址。

```
==================================================
  系统启动成功！
  本机访问：  http://localhost:8080
  局域网访问：http://192.168.1.23:8080 （手机需连接同一 Wi-Fi）
  维修人员账号：W001 / W002 / W003，初始密码 123456
  演示学生账号：2023001，初始密码 123456
  ...
==================================================
```

注意事项：

- **使用期间不要关闭这个黑色窗口**，关闭窗口系统就会停止。
- 首次启动时如果弹出 **Windows 防火墙** 提示，想用手机访问就勾选"专用网络"并点"允许访问"；只在本机访问可以直接取消。
- 如果启动失败，窗口会显示原因（如"MySQL 用户名或密码错误"），按提示修改后重新双击 start.bat。详细错误在 `logs\app.log`。
- 系统已经在运行时再次双击 start.bat，只会打开浏览器，不会重复启动。

### 4.4 停止

任选一种：关闭启动窗口；在启动窗口按 **Ctrl+C**（提示"终止批处理操作吗"时输入 Y）；或双击 **stop.bat**。

stop.bat 只会停止本系统的 java 进程，不会误关其他程序。

### 4.5 部署到另一台电脑（如答辩用的笔记本）

目标电脑**只需要 JDK 17 和 MySQL**，不需要 IDEA、Maven 和源代码：

1. 按第 1 节在目标电脑安装 JDK 17 和 MySQL；
2. 把 `dist\dorm-repair-windows.zip` 拷过去，右键 → 属性 → 解除锁定，解压到如 `D:\dorm-repair-app`；
3. 按 4.2 修改 `application.properties` 中的数据库密码；
4. 双击 `start.bat`。

压缩包中的配置文件是默认值（不包含你电脑上的密码），日志和上传图片也不会被打包。

### 4.6 更新版本

修改代码后重新双击 `build.bat` 即可：脚本只替换 `dorm-repair.jar` 和脚本文件，**保留**你修改过的 `application.properties`、`uploads\` 图片和 `logs\` 日志；数据库中的数据也不受影响。如果系统正在运行，请先停止再打包。

部署在其他电脑上的，只需用新的 `dorm-repair.jar` 替换旧文件，然后重新启动。

### 4.7 不使用脚本的手动启动（脚本无法运行时的备用方法）

```bat
cd /d D:\dorm-repair-app
java -jar dorm-repair.jar
```

程序会自动读取当前目录下的 `application.properties`。窗口出现 `Started DormRepairApplication` 后访问 <http://localhost:8080>。

---

## 5. 系统操作步骤（演示流程）

下面是一次完整的业务闭环，答辩时可以照此演示。

**学生提交报修**

1. 打开 <http://localhost:8080>，点"学生注册"，填写学号、姓名、楼栋、宿舍号、手机号、密码 → 注册（或直接用演示账号 2023001 / 123456）。
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

- **角色权限隔离**：学生登录后，在地址栏直接输入 `http://localhost:8080/repair/pending`，返回"403 无权访问"。
- **防止重复接单**：开两个浏览器窗口（一个普通窗口、一个无痕窗口），分别登录 W001 和 W002，同时打开同一张待接单工单；先后点"接单"，后点的一方提示"手慢了，该工单已被其他维修人员接单"。
- **数据持久化**：停止系统后重新启动，之前的工单和评价都还在。

**清空测试数据**（答辩前恢复成初始状态）：在 MySQL 中执行下面的语句，然后重新启动系统，会重新建表并录入维修人员账号；同时可删除部署目录下的 `uploads` 文件夹。

```sql
DROP DATABASE dorm_repair;
```

---

## 6. 数据备份与恢复

数据包括两部分：MySQL 中的 `dorm_repair` 数据库，以及部署目录下的 `uploads\` 图片文件夹。

**备份数据库**（路径中的版本号按实际安装修改）：

```bat
"C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqldump.exe" -uroot -p --default-character-set=utf8mb4 --databases dorm_repair > D:\backup\dorm_repair.sql
```

**恢复数据库**：

```bat
"C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe" -uroot -p --default-character-set=utf8mb4 < D:\backup\dorm_repair.sql
```

**备份图片**：直接复制 `uploads` 文件夹。

---

## 7. 常见问题

| 现象 | 原因与解决办法 |
|---|---|
| 双击 .bat 弹出"Windows 已保护你的电脑"或安全警告 | 文件来自网络下载。点"更多信息 → 仍要运行"；或在解压前右键 ZIP → 属性 → 解除锁定 |
| 提示"未找到 Java 17 或更高版本" | 未安装 JDK 17，或安装后没有重新打开窗口。按 1.1 安装后重试 |
| 打包提示"未找到 JDK"，但 `java -version` 是 17 | 电脑上只有 JRE，没有 javac 编译器。安装完整 JDK（不是 JRE） |
| 提示"无法连接到 MySQL" | MySQL 服务没启动：`services.msc` 中启动 MySQL 服务，或以管理员身份运行 `net start MySQL84`（服务名以实际为准） |
| 提示"MySQL 用户名或密码错误"（Access denied） | 修改 `application.properties`（方式二）或 `application.yml`（方式一）中的密码 |
| 提示"端口 8080 已被占用" | IDEA 中项目还在运行（先停止），或其他软件占用了端口：修改 `server.port=8081` 后访问 `http://localhost:8081`。查看占用者：`netstat -ano \| findstr :8080`，结束进程：`taskkill /PID 进程号 /F` |
| 打包时下载依赖很慢或失败 | 检查网络后重新运行；仍失败可删除 `%USERPROFILE%\.m2\repository` 中报错的目录后重试；国外网络使用 `build.bat -NoMirror` |
| 运行 build.bat 提示"禁止运行脚本" | 学校机房等电脑的组策略禁止了 PowerShell 脚本。方式二改用 IDEA 中右侧 Maven 面板 → Lifecycle → package 打包，然后按 4.7 手动启动 |
| 点击黑窗口后日志不滚动了 | 窗口进入了"选择"状态（标题栏出现"选择"），按 Esc 恢复。系统本身不受影响，仍正常运行 |
| 手机无法访问 | 手机与电脑必须连同一个 Wi-Fi；使用启动窗口显示的"局域网访问"地址；在 Windows 防火墙中允许 Java（OpenJDK Platform binary）在专用网络通信；校园网可能禁止设备互访，可改用手机热点 |
| 页面中文乱码 / MySQL 命令行中文显示为问号 | 页面乱码请确认 IDEA 文件编码为 UTF-8（见第 3 节第 4 步）；命令行登录 MySQL 时加 `--default-character-set=utf8mb4` |
| 上传图片提示"图片大小不能超过 5MB" | 系统限制单张图片 5MB，请压缩后再上传 |
