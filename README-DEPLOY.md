# Linux服务器部署指南

## 📦 一、本地打包

### Windows系统

```bash
# 方法1: 使用批处理脚本
package.bat

# 方法2: 使用Maven命令
mvn clean package -DskipTests
```

### Linux/Mac系统

```bash
# 方法1: 使用Shell脚本
chmod +x package.sh
./package.sh

# 方法2: 使用Maven命令
mvn clean package -DskipTests
```

### 打包结果

打包完成后，JAR文件位于：
```
target/quota-matching-tool-1.0.0.jar
```

文件大小约 **60MB**，包含所有依赖，可直接运行。

---

## 🚀 二、上传到Linux服务器

### 方法1: 使用SCP命令

```bash
# 从Windows上传（使用PowerShell或Git Bash）
scp target/quota-matching-tool-1.0.0.jar user@your-server-ip:/tmp/

# 从Linux/Mac上传
scp target/quota-matching-tool-1.0.0.jar user@your-server-ip:/tmp/
```

### 方法2: 使用FTP/SFTP工具

使用 FileZilla、WinSCP 等工具上传 JAR 文件到服务器的 `/tmp/` 目录。

---

## 🔧 三、服务器部署

### 方式1: 使用自动部署脚本（推荐）

1. **上传部署脚本到服务器**

```bash
# 将 deploy-linux.sh 上传到服务器
scp deploy-linux.sh user@your-server-ip:/tmp/
```

2. **在服务器上执行部署脚本**

```bash
# SSH登录服务器
ssh user@your-server-ip

# 进入项目目录（如果JAR文件在项目目录中）
cd /path/to/quota-matching-tool

# 或者将JAR文件复制到项目目录
cp /tmp/quota-matching-tool-1.0.0.jar target/

# 执行部署脚本（需要root权限）
sudo chmod +x deploy-linux.sh
sudo ./deploy-linux.sh
```

### 方式2: 手动部署

#### 1. 创建部署目录

```bash
sudo mkdir -p /opt/quota-matching-tool
sudo mkdir -p /var/log/quota-matching-tool
```

#### 2. 复制JAR文件

```bash
sudo cp /tmp/quota-matching-tool-1.0.0.jar /opt/quota-matching-tool/quota-matching-tool.jar
sudo chmod 755 /opt/quota-matching-tool/quota-matching-tool.jar
```

#### 3. 创建应用用户

```bash
sudo useradd -r -s /bin/false quota-matching-tool
sudo chown -R quota-matching-tool:quota-matching-tool /opt/quota-matching-tool
sudo chown -R quota-matching-tool:quota-matching-tool /var/log/quota-matching-tool
```

#### 4. 创建启动脚本

创建 `/opt/quota-matching-tool/start.sh`:

```bash
#!/bin/bash
cd /opt/quota-matching-tool

export SPRING_PROFILES_ACTIVE=prod
export DB_USERNAME=root
export DB_PASSWORD=your_password
export LOG_PATH=/var/log/quota-matching-tool/application.log

java -Xms512m -Xmx1024m -jar quota-matching-tool.jar
```

设置执行权限：

```bash
sudo chmod +x /opt/quota-matching-tool/start.sh
sudo chown quota-matching-tool:quota-matching-tool /opt/quota-matching-tool/start.sh
```

#### 5. 创建Systemd服务

创建 `/etc/systemd/system/quota-matching-tool.service`:

```ini
[Unit]
Description=Enterprise Quota Matching Tool
After=network.target mysql.service
Wants=network.target

[Service]
Type=simple
User=quota-matching-tool
WorkingDirectory=/opt/quota-matching-tool
Environment="SPRING_PROFILES_ACTIVE=prod"
Environment="JAVA_OPTS=-Xms512m -Xmx1024m"
Environment="LOG_PATH=/var/log/quota-matching-tool/application.log"
Environment="DB_USERNAME=root"
Environment="DB_PASSWORD=your_password"
ExecStart=/usr/bin/java $JAVA_OPTS -jar /opt/quota-matching-tool/quota-matching-tool.jar
ExecStop=/bin/kill -15 $MAINPID
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=quota-matching-tool

[Install]
WantedBy=multi-user.target
```

**重要：** 请修改 `DB_PASSWORD` 为实际的数据库密码！

#### 6. 重载Systemd并启动服务

```bash
sudo systemctl daemon-reload
sudo systemctl start quota-matching-tool
sudo systemctl enable quota-matching-tool
```

---

## ✅ 四、验证部署

### 1. 检查服务状态

```bash
sudo systemctl status quota-matching-tool
```

### 2. 查看日志

```bash
# 使用journalctl查看
sudo journalctl -u quota-matching-tool -f

# 或查看应用日志文件
tail -f /var/log/quota-matching-tool/application.log
```

### 3. 检查端口

```bash
sudo netstat -tlnp | grep 8080
# 或
sudo ss -tlnp | grep 8080
```

### 4. 测试访问

在浏览器中访问：
```
http://your-server-ip:8080
```

---

## 🗄️ 五、数据库配置

### 1. 确保MySQL服务运行

```bash
sudo systemctl status mysql
# 或
sudo systemctl status mariadb
```

### 2. 创建数据库

```bash
mysql -u root -p

# 在MySQL中执行
CREATE DATABASE quota_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
GRANT ALL PRIVILEGES ON quota_db.* TO 'root'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

### 3. 配置数据库连接

编辑服务文件：

```bash
```

修改以下环境变量：
```ini
Environment="DB_USERNAME=your_username"
Environment="DB_PASSWORD=your_password"
```

然后重启服务：

```bash
sudo systemctl daemon-reload
sudo systemctl restart quota-matching-tool
```

---

## 🔥 六、防火墙配置

```bash
# Ubuntu/Debian
sudo ufw allow 8080/tcp
sudo ufw reload

# CentOS/RHEL
sudo firewall-cmd --permanent --add-port=8080/tcp
sudo firewall-cmd --reload
```

---

## 📝 七、常用管理命令

### 启动服务
```bash
sudo systemctl start quota-matching-tool
```

### 停止服务
```bash
sudo systemctl stop quota-matching-tool
```

### 重启服务
```bash
sudo systemctl restart quota-matching-tool
```

### 查看服务状态
```bash
sudo systemctl status quota-matching-tool
```

### 查看实时日志
```bash
sudo journalctl -u quota-matching-tool -f
```

### 查看最近100行日志
```bash
sudo journalctl -u quota-matching-tool -n 100
```

### 禁用开机自启
```bash
sudo systemctl disable quota-matching-tool
```

### 启用开机自启
```bash
sudo systemctl enable quota-matching-tool
```

---

## 🔄 八、更新部署

### 1. 停止服务

```bash
sudo systemctl stop quota-matching-tool
```

### 2. 备份旧JAR

```bash
sudo cp /opt/quota-matching-tool/quota-matching-tool.jar /opt/quota-matching-tool/quota-matching-tool.jar.bak.$(date +%Y%m%d_%H%M%S)
```

### 3. 上传新JAR

```bash
# 上传新JAR文件到 /tmp/
scp target/quota-matching-tool-1.0.0.jar user@server:/tmp/

# 在服务器上复制
sudo cp /tmp/quota-matching-tool-1.0.0.jar /opt/quota-matching-tool/quota-matching-tool.jar
sudo chown quota-matching-tool:quota-matching-tool /opt/quota-matching-tool/quota-matching-tool.jar
```

### 4. 启动服务

```bash
sudo systemctl start quota-matching-tool
sudo systemctl status quota-matching-tool
```

---

## ⚠️ 九、常见问题

### 1. 服务启动失败

**检查日志：**
```bash
sudo journalctl -u quota-matching-tool -n 50
```

**常见原因：**
- 数据库连接失败：检查数据库服务是否运行，用户名密码是否正确
- 端口被占用：检查8080端口是否被其他程序占用
- 内存不足：调整JAVA_OPTS中的内存参数

### 2. 无法访问应用

**检查：**
- 防火墙是否开放8080端口
- 服务是否正常运行：`sudo systemctl status quota-matching-tool`
- 端口是否监听：`sudo netstat -tlnp | grep 8080`

### 3. 数据库连接错误

**检查：**
- MySQL服务是否运行
- 数据库是否已创建
- 用户名密码是否正确
- 数据库用户是否有权限

### 4. 内存不足

**调整JVM参数：**
编辑服务文件，修改 `JAVA_OPTS`：
```ini
Environment="JAVA_OPTS=-Xms1024m -Xmx2048m"
```

然后重启服务。

---

## 📞 十、技术支持

如遇到问题，请检查：
1. 应用日志：`/var/log/quota-matching-tool/application.log`
2. 系统日志：`sudo journalctl -u quota-matching-tool`
3. 数据库日志：MySQL错误日志

---

## 📋 部署检查清单

- [ ] JAR文件已成功打包
- [ ] JAR文件已上传到服务器
- [ ] 部署目录已创建
- [ ] 应用用户已创建
- [ ] Systemd服务文件已创建
- [ ] 数据库已创建并配置
- [ ] 服务已启动并运行正常
- [ ] 防火墙已配置
- [ ] 可以正常访问应用

---

**部署完成后，访问：** `http://your-server-ip:8080`

