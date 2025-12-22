# 生产环境部署文档

## 📋 目录

- [系统要求](#系统要求)
- [部署前准备](#部署前准备)
- [数据库配置](#数据库配置)
- [应用部署](#应用部署)
- [服务管理](#服务管理)
- [监控与日志](#监控与日志)
- [故障排查](#故障排查)

## 系统要求

### 硬件要求
- CPU: 2核及以上
- 内存: 2GB及以上（推荐4GB）
- 磁盘: 10GB及以上可用空间

### 软件要求
- 操作系统: Linux (CentOS 7+, Ubuntu 18.04+)
- Java: JDK 1.8 或更高版本
- 数据库: MySQL 5.7+ 或 MySQL 8.0+
- Maven: 3.6+ (仅用于打包)

## 部署前准备

### 1. 安装Java环境

```bash
# CentOS/RHEL
sudo yum install java-1.8.0-openjdk java-1.8.0-openjdk-devel

# Ubuntu/Debian
sudo apt-get update
sudo apt-get install openjdk-8-jdk

# 验证安装
java -version
```

### 2. 安装MySQL数据库

```bash
# CentOS/RHEL
sudo yum install mysql-server
sudo systemctl start mysqld
sudo systemctl enable mysqld

# Ubuntu/Debian
sudo apt-get install mysql-server
sudo systemctl start mysql
sudo systemctl enable mysql
```

### 3. 创建应用目录

```bash
sudo mkdir -p /opt/quota-matching-tool
sudo mkdir -p /var/log/quota-matching-tool
sudo chown -R $USER:$USER /opt/quota-matching-tool
sudo chown -R $USER:$USER /var/log/quota-matching-tool
```

## 数据库配置

### 1. 创建数据库和用户

```sql
-- 登录MySQL
mysql -u root -p

-- 创建数据库
CREATE DATABASE quota_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建用户（可选，建议使用独立用户）
CREATE USER 'quota_user'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON quota_db.* TO 'quota_user'@'localhost';
FLUSH PRIVILEGES;
```

### 2. 配置数据库连接

编辑 `src/main/resources/application-prod.properties` 或使用环境变量：

```bash
export DB_HOST=localhost
export DB_PORT=3306
export DB_NAME=quota_db
export DB_USERNAME=root
export DB_PASSWORD=your_password
```

## 应用部署

### 1. 打包应用

在项目根目录执行：

```bash
mvn clean package -DskipTests
```

打包完成后，JAR文件位于：`target/quota-matching-tool-1.0.0.jar`

### 2. 上传文件到服务器

```bash
# 上传JAR文件
scp target/quota-matching-tool-1.0.0.jar user@server:/opt/quota-matching-tool/target/

# 上传启动脚本
scp start.sh stop.sh user@server:/opt/quota-matching-tool/

# 设置执行权限
chmod +x /opt/quota-matching-tool/start.sh
chmod +x /opt/quota-matching-tool/stop.sh
```

### 3. 配置环境变量（可选）

创建配置文件 `/opt/quota-matching-tool/.env`：

```bash
# 数据库配置
export DB_HOST=localhost
export DB_PORT=3306
export DB_NAME=quota_db
export DB_USERNAME=root
export DB_PASSWORD=your_password

# 日志路径
export LOG_PATH=/var/log/quota-matching-tool
```

### 4. 启动应用

#### 方式一：使用启动脚本（推荐）

```bash
cd /opt/quota-matching-tool
./start.sh
```

#### 方式二：使用systemd服务

```bash
# 复制服务文件
sudo cp quota-matching-tool.service /etc/systemd/system/

# 编辑服务文件，修改路径和配置
sudo vi /etc/systemd/system/quota-matching-tool.service

# 重新加载systemd
sudo systemctl daemon-reload

# 启动服务
sudo systemctl start quota-matching-tool

# 设置开机自启
sudo systemctl enable quota-matching-tool

# 查看状态
sudo systemctl status quota-matching-tool
```

#### 方式三：手动启动

```bash
cd /opt/quota-matching-tool
nohup java -Xms512m -Xmx1024m -XX:+UseG1GC \
  -Djava.awt.headless=true \
  -Dfile.encoding=UTF-8 \
  -Duser.timezone=Asia/Shanghai \
  -jar target/quota-matching-tool-1.0.0.jar \
  --spring.profiles.active=prod \
  > logs/startup.log 2>&1 &
```

## 服务管理

### 使用启动脚本

```bash
# 启动
./start.sh

# 停止
./stop.sh

# 查看进程
ps aux | grep quota-matching-tool
```

### 使用systemd

```bash
# 启动
sudo systemctl start quota-matching-tool

# 停止
sudo systemctl stop quota-matching-tool

# 重启
sudo systemctl restart quota-matching-tool

# 查看状态
sudo systemctl status quota-matching-tool

# 查看日志
sudo journalctl -u quota-matching-tool -f
```

## 监控与日志

### 日志位置

- 应用日志: `/var/log/quota-matching-tool/application.log`
- 错误日志: `/var/log/quota-matching-tool/error.log`
- 启动日志: `logs/startup.log` (使用脚本启动时)

### 查看日志

```bash
# 实时查看应用日志
tail -f /var/log/quota-matching-tool/application.log

# 查看错误日志
tail -f /var/log/quota-matching-tool/error.log

# 查看最近100行
tail -n 100 /var/log/quota-matching-tool/application.log
```

### 健康检查

应用提供健康检查端点：

```bash
# 检查应用健康状态
curl http://localhost:8080/actuator/health

# 查看应用信息
curl http://localhost:8080/actuator/info
```

### 端口检查

```bash
# 检查端口是否监听
netstat -tlnp | grep 8080
# 或
ss -tlnp | grep 8080
```

## 故障排查

### 1. 应用无法启动

**检查Java版本：**
```bash
java -version
```

**检查端口占用：**
```bash
netstat -tlnp | grep 8080
```

**查看启动日志：**
```bash
tail -f logs/startup.log
```

### 2. 数据库连接失败

**检查MySQL服务：**
```bash
sudo systemctl status mysql
```

**测试数据库连接：**
```bash
mysql -h localhost -u root -p quota_db
```

**检查防火墙：**
```bash
sudo firewall-cmd --list-ports
```

### 3. 内存不足

**查看内存使用：**
```bash
free -h
```

**调整JVM参数：**
编辑 `start.sh` 或 `quota-matching-tool.service`，修改 `-Xmx` 参数

### 4. 文件上传失败

**检查文件大小限制：**
编辑 `application-prod.properties`，调整：
```properties
spring.servlet.multipart.max-file-size=50MB
spring.servlet.multipart.max-request-size=50MB
```

**检查磁盘空间：**
```bash
df -h
```

### 5. 性能优化

**数据库连接池配置：**
编辑 `application-prod.properties`：
```properties
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
```

**JVM参数优化：**
根据服务器配置调整 `start.sh` 中的JVM参数

## 安全建议

1. **使用非root用户运行应用**
2. **配置防火墙规则，限制访问**
3. **使用HTTPS（建议使用Nginx反向代理）**
4. **定期备份数据库**
5. **限制Actuator端点访问**
6. **使用强密码**
7. **定期更新依赖包**

## 备份与恢复

### 数据库备份

```bash
# 备份
mysqldump -u root -p quota_db > backup_$(date +%Y%m%d).sql

# 恢复
mysql -u root -p quota_db < backup_20231220.sql
```

### 应用备份

```bash
# 备份JAR文件
cp target/quota-matching-tool-1.0.0.jar backup/
```

## 更新部署

1. 停止应用
2. 备份当前版本
3. 上传新版本JAR文件
4. 启动应用
5. 验证功能

```bash
./stop.sh
cp target/quota-matching-tool-1.0.0.jar backup/
# 上传新版本
./start.sh
```

## 联系支持

如遇到问题，请查看日志文件或联系技术支持。

