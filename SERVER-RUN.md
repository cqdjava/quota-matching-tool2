# Linux服务器运行指南

## ⚠️ 重要说明

**不要在宝塔面板中直接运行Java应用！**

宝塔面板是Python编写的，不适合直接运行Java应用。应该使用SSH终端直接运行。

## 🚀 正确的运行方式

### 方式一：使用SSH终端运行（推荐）

#### 1. 连接到服务器
```bash
ssh root@your_server_ip
```

#### 2. 上传文件到服务器
```bash
# 在本地使用scp上传
scp target/quota-matching-tool-1.0.0.jar root@your_server_ip:/opt/quota-matching-tool/
scp start.sh stop.sh root@your_server_ip:/opt/quota-matching-tool/
```

#### 3. 设置权限
```bash
cd /opt/quota-matching-tool
chmod +x start.sh stop.sh
```

#### 4. 配置数据库（如果使用生产环境）
```bash
# 设置环境变量
export DB_HOST=localhost
export DB_PORT=3306
export DB_NAME=quota_db
export DB_USERNAME=root
export DB_PASSWORD=your_password

# 或创建配置文件
vi .env
```

#### 5. 启动应用
```bash
# 后台启动（推荐）
./start.sh

# 或前台运行（查看日志）
./run.sh prod
```

### 方式二：使用systemd服务（推荐生产环境）

#### 1. 创建服务文件
```bash
sudo vi /etc/systemd/system/quota-matching-tool.service
```

#### 2. 复制服务配置
将项目中的 `quota-matching-tool.service` 文件内容复制到上述文件，并修改路径。

#### 3. 启动服务
```bash
sudo systemctl daemon-reload
sudo systemctl start quota-matching-tool
sudo systemctl enable quota-matching-tool  # 开机自启
sudo systemctl status quota-matching-tool  # 查看状态
```

### 方式三：使用nohup直接运行

```bash
cd /opt/quota-matching-tool

# 设置环境变量
export DB_PASSWORD=your_password

# 后台运行
nohup java -Xms512m -Xmx1024m -jar target/quota-matching-tool-1.0.0.jar \
  --spring.profiles.active=prod \
  > logs/app.log 2>&1 &

# 查看日志
tail -f logs/app.log
```

## 📋 完整部署步骤

### 1. 准备服务器环境

```bash
# 安装Java（如果没有）
# CentOS/RHEL
yum install java-1.8.0-openjdk java-1.8.0-openjdk-devel

# Ubuntu/Debian
apt-get update
apt-get install openjdk-8-jdk

# 验证安装
java -version
```

### 2. 创建应用目录

```bash
mkdir -p /opt/quota-matching-tool
mkdir -p /opt/quota-matching-tool/logs
mkdir -p /var/log/quota-matching-tool
chmod 755 /opt/quota-matching-tool
```

### 3. 上传文件

```bash
# 上传JAR文件
scp target/quota-matching-tool-1.0.0.jar root@server:/opt/quota-matching-tool/target/

# 上传脚本
scp start.sh stop.sh run.sh root@server:/opt/quota-matching-tool/
```

### 4. 配置数据库

```bash
# 登录MySQL
mysql -u root -p

# 创建数据库
CREATE DATABASE quota_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 退出MySQL
exit
```

### 5. 配置环境变量

```bash
cd /opt/quota-matching-tool

# 创建环境变量文件
cat > .env << EOF
DB_HOST=localhost
DB_PORT=3306
DB_NAME=quota_db
DB_USERNAME=root
DB_PASSWORD=your_password
LOG_PATH=/var/log/quota-matching-tool
EOF

# 加载环境变量
source .env
```

### 6. 启动应用

```bash
cd /opt/quota-matching-tool
chmod +x *.sh
./start.sh
```

### 7. 验证运行

```bash
# 查看进程
ps aux | grep quota-matching-tool

# 查看日志
tail -f logs/application.log

# 测试访问
curl http://localhost:8080/actuator/health
```

## 🔧 宝塔面板用户注意事项

### 如果必须使用宝塔面板

1. **不要通过宝塔面板的"终端"功能运行Java应用**
   - 宝塔面板的终端可能有问题

2. **使用SSH客户端连接**
   - 使用PuTTY、Xshell、MobaXterm等SSH客户端
   - 或使用系统自带的终端

3. **在宝塔面板中配置反向代理（可选）**
   - 如果应用运行在8080端口
   - 可以在宝塔面板中配置Nginx反向代理
   - 将域名指向 `http://localhost:8080`

### 宝塔面板Nginx配置示例

```nginx
server {
    listen 80;
    server_name your_domain.com;
    
    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

## 🐛 常见问题

### 1. 权限问题

```bash
# 如果提示权限不足
chmod +x start.sh stop.sh run.sh
chmod 755 /opt/quota-matching-tool
```

### 2. 端口被占用

```bash
# 查看端口占用
netstat -tlnp | grep 8080
# 或
ss -tlnp | grep 8080

# 停止占用进程
kill -9 <PID>
```

### 3. 数据库连接失败

```bash
# 检查MySQL服务
systemctl status mysql
# 或
systemctl status mysqld

# 启动MySQL
systemctl start mysql
```

### 4. 查看详细错误

```bash
# 查看启动日志
cat logs/startup.log

# 查看应用日志
tail -f logs/application.log

# 查看错误日志
tail -f /var/log/quota-matching-tool/error.log
```

## 📞 获取帮助

如果遇到问题：

1. 查看日志文件：`logs/application.log`
2. 检查环境变量是否正确设置
3. 确认数据库服务是否运行
4. 确认端口是否被占用


