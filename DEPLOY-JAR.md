# JAR包打包和部署指南

## 一、本地打包

### Windows系统

1. **使用批处理脚本（推荐）**
   ```bash
   package.bat
   ```

2. **手动打包**
   ```bash
   mvn clean package -DskipTests
   ```

### Linux/Mac系统

1. **使用Shell脚本（推荐）**
   ```bash
   chmod +x package.sh
   ./package.sh
   ```

2. **手动打包**
   ```bash
   mvn clean package -DskipTests
   ```

## 二、打包后的文件

打包完成后，JAR文件位于：
```
target/quota-matching-tool-1.0.0.jar
```

这是一个**可执行的Fat JAR**，包含了所有依赖，可以直接运行。

## 三、上传到云服务器

### 方法1：使用SCP命令

```bash
# 从本地Windows上传到Linux服务器
scp target/quota-matching-tool-1.0.0.jar user@your-server-ip:/opt/quota-matching-tool/

# 从本地Linux/Mac上传
scp target/quota-matching-tool-1.0.0.jar user@your-server-ip:/opt/quota-matching-tool/
```

### 方法2：使用FTP/SFTP工具

使用FileZilla、WinSCP等工具上传JAR文件到服务器。

### 方法3：使用Git

如果服务器已配置Git，可以：
```bash
# 在服务器上
git pull
mvn clean package -DskipTests
```

## 四、服务器部署

### 1. 创建部署目录

```bash
sudo mkdir -p /opt/quota-matching-tool
sudo chown $USER:$USER /opt/quota-matching-tool
cd /opt/quota-matching-tool
```

### 2. 上传JAR文件

将打包好的JAR文件上传到 `/opt/quota-matching-tool/` 目录。

### 3. 创建启动脚本

创建 `start.sh`：
```bash
#!/bin/bash
cd /opt/quota-matching-tool

# 设置Java环境变量（根据实际情况修改）
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

# 设置生产环境配置
export SPRING_PROFILES_ACTIVE=prod

# 设置数据库连接（根据实际情况修改）
export DB_USERNAME=root
export DB_PASSWORD=your_password

# 设置日志路径
export LOG_PATH=/var/log/quota-matching-tool/application.log

# 创建日志目录
sudo mkdir -p /var/log/quota-matching-tool
sudo chown $USER:$USER /var/log/quota-matching-tool

# 启动应用
nohup java -jar quota-matching-tool-1.0.0.jar > /dev/null 2>&1 &

echo "应用已启动，PID: $!"
echo "查看日志: tail -f /var/log/quota-matching-tool/application.log"
```

### 4. 创建停止脚本

创建 `stop.sh`：
```bash
#!/bin/bash
PID=$(ps -ef | grep quota-matching-tool-1.0.0.jar | grep -v grep | awk '{print $2}')

if [ -z "$PID" ]; then
    echo "应用未运行"
else
    echo "正在停止应用，PID: $PID"
    kill $PID
    sleep 3
    
    # 如果还在运行，强制杀死
    if ps -p $PID > /dev/null; then
        echo "强制停止应用"
        kill -9 $PID
    fi
    
    echo "应用已停止"
fi
```

### 5. 设置执行权限

```bash
chmod +x start.sh stop.sh
```

### 6. 启动应用

```bash
./start.sh
```

### 7. 检查运行状态

```bash
# 查看进程
ps -ef | grep quota-matching-tool

# 查看日志
tail -f /var/log/quota-matching-tool/application.log

# 检查端口
netstat -tlnp | grep 8080
```

## 五、使用Systemd服务（推荐）

### 1. 创建服务文件

创建 `/etc/systemd/system/quota-matching-tool.service`：

```ini
[Unit]
Description=Quota Matching Tool
After=network.target mysql.service

[Service]
Type=simple
User=your-user
WorkingDirectory=/opt/quota-matching-tool
Environment="SPRING_PROFILES_ACTIVE=prod"
Environment="DB_USERNAME=root"
Environment="DB_PASSWORD=your_password"
Environment="LOG_PATH=/var/log/quota-matching-tool/application.log"
ExecStart=/usr/bin/java -jar /opt/quota-matching-tool/quota-matching-tool-1.0.0.jar
ExecStop=/bin/kill -15 $MAINPID
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

### 2. 重载systemd配置

```bash
sudo systemctl daemon-reload
```

### 3. 启动服务

```bash
sudo systemctl start quota-matching-tool
sudo systemctl enable quota-matching-tool  # 开机自启
```

### 4. 查看服务状态

```bash
sudo systemctl status quota-matching-tool
```

### 5. 查看日志

```bash
sudo journalctl -u quota-matching-tool -f
```

## 六、配置Nginx反向代理（可选）

如果需要通过80端口访问，可以配置Nginx：

```nginx
server {
    listen 80;
    server_name your-domain.com;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

## 七、防火墙配置

```bash
# 开放8080端口
sudo ufw allow 8080/tcp
sudo ufw reload
```

## 八、常见问题

### 1. 端口被占用

```bash
# 查看端口占用
sudo netstat -tlnp | grep 8080

# 或者修改配置文件中的端口
# 在application-prod.properties中修改server.port
```

### 2. 数据库连接失败

- 检查MySQL服务是否运行：`sudo systemctl status mysql`
- 检查数据库用户名密码是否正确
- 检查数据库是否已创建：`mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS quota_db;"`

### 3. 内存不足

在启动脚本中增加JVM参数：
```bash
java -Xms512m -Xmx1024m -jar quota-matching-tool-1.0.0.jar
```

### 4. 查看应用日志

```bash
tail -f /var/log/quota-matching-tool/application.log
```

## 九、更新部署

1. 停止应用：`./stop.sh` 或 `sudo systemctl stop quota-matching-tool`
2. 备份旧JAR：`cp quota-matching-tool-1.0.0.jar quota-matching-tool-1.0.0.jar.bak`
3. 上传新JAR文件
4. 启动应用：`./start.sh` 或 `sudo systemctl start quota-matching-tool`

## 十、验证部署

访问应用：
```
http://your-server-ip:8080
```

如果配置了Nginx：
```
http://your-domain.com
```

