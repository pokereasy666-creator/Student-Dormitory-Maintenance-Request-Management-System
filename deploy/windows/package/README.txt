学生宿舍报修管理系统 - Windows 部署包
==================================================

【运行环境】
  1. JDK 17 或更高版本（推荐 Eclipse Temurin 17）
  2. MySQL 8（安装时记住 root 密码，并设置为开机自动启动的 Windows 服务）

【首次部署】
  1. 用记事本打开 application.properties，把 DB_PASSWORD=root 中的 root
     改成你的 MySQL 密码，保存。
  2. 双击 start.bat。脚本会自动检查 Java、端口和 MySQL，然后启动系统，
     看到"系统启动成功"后会自动打开浏览器（http://localhost:8080）。
     数据库 dorm_repair 和数据表会在首次启动时自动创建，不需要手动导入 SQL。
  3. 使用期间不要关闭黑色窗口；关闭窗口或按 Ctrl+C 即停止系统。

【默认账号】（初始密码都是 123456）
  维修人员：W001、W002、W003
  演示学生：2023001（学生也可以在登录页自行注册）

【文件说明】
  dorm-repair.jar          系统程序
  application.properties   配置文件（数据库密码、端口）
  start.bat / stop.bat     启动 / 停止
  logs\app.log             运行日志（出错时查看这里）
  uploads\                 学生上传的报修图片（请勿删除）

【常见问题】
  - 双击 start.bat 弹出安全警告：点"运行"。也可以在解压前右键 zip 文件
    -> 属性 -> 勾选"解除锁定"。
  - 提示 MySQL 用户名或密码错误：修改 application.properties 中的 DB_PASSWORD。
  - 提示端口 8080 被占用：修改 application.properties 中的 server.port，
    或关闭 IDEA 中正在运行的项目。
  - 手机无法访问：手机和电脑连同一 Wi-Fi；首次启动时 Windows 防火墙弹窗
    请允许"专用网络"访问。

完整说明见项目中的 docs/windows-deploy.md。
