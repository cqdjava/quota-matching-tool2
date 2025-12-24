# 快速部署指南

## 🚀 三步快速部署

### 第一步：打包（在本地Windows）

```bash
# 运行打包脚本
package.bat

# 或使用Maven命令
mvn clean package -DskipTests
```

打包完成后，JAR文件位于：`target/quota-matching-tool-1.0.0.jar` (约59MB)

---

### 第二步：上传到Linux服务器

```bash
# 使用SCP上传（在PowerShell或Git Bash中执行）
scp target/quota-matching-tool-1.0.0.jar root@your-server-ip:/tmp/
```

**注意：** 将 `your-server-ip` 替换为实际的服务器IP地址

---

### 第三步：在服务器上部署

#### 方式A：使用自动部署脚本（推荐）

```bash
# 1. SSH登录服务器
ssh root@your-server-ip

# 2. 上传部署脚本（在本地执行）
scp deploy-linux.sh root@your-server-ip:/tmp/

# 3. 在服务器上执行
cd /tmp
chmod +x deploy-linux.sh
./deploy-linux.sh

# 4. 编辑服务文件，配置数据库密码
nano /etc/systemd/system/quota-matching-tool.service
# 修改 Environment="DB_PASSWORD=your_password"

# 5. 启动服务
systemctl daemon-reload
systemctl start quota-matching-tool
systemctl enable quota-matching-tool

# 6. 检查状态
systemctl status quota-matching-tool
```

#### 方式B：手动部署（5分钟）

```bash
# 1. 创建目录
mkdir -p /opt/quota-matching-tool
mkdir -p /var/log/quota-matching-tool

# 2. 复制JAR文件
cp /tmp/quota-matching-tool-1.0.0.jar /opt/quota-matching-tool/quota-matching-tool.jar

# 3. 创建服务文件
cat > /etc/systemd/system/quota-matching-tool.service << 'EOF'
[Unit]
Description=Enterprise Quota Matching Tool
After=network.target mysql.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/quota-matching-tool
Environment="SPRING_PROFILES_ACTIVE=prod"
Environment="JAVA_OPTS=-Xms512m -Xmx1024m"
Environment="DB_USERNAME=root"
Environment="DB_PASSWORD=your_password"
ExecStart=/usr/bin/java $JAVA_OPTS -jar /opt/quota-matching-tool/quota-matching-tool.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

# 4. 编辑服务文件，修改数据库密码
nano /etc/systemd/system/quota-matching-tool.service

# 5. 启动服务
systemctl daemon-reload
systemctl start quota-matching-tool
systemctl enable quota-matching-tool
systemctl status quota-matching-tool
```

---

## ✅ 验证部署

```bash
# 查看服务状态
systemctl status quota-matching-tool

# 查看日志
journalctl -u quota-matching-tool -f

# 检查端口
netstat -tlnp | grep 8080
```

在浏览器访问：`http://your-server-ip:8080`

---

## 🔧 配置数据库

```bash
# 登录MySQL
mysql -u root -p

# 创建数据库
CREATE DATABASE quota_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
EXIT;
```

---

## 📝 常用命令

```bash
# 启动
systemctl start quota-matching-tool

# 停止
systemctl stop quota-matching-tool

# 重启
systemctl restart quota-matching-tool

# 查看日志
journalctl -u quota-matching-tool -f
```

---

## ⚠️ 注意事项

1. **数据库密码**：务必在服务文件中配置正确的数据库密码
2. **防火墙**：确保8080端口已开放
3. **Java版本**：确保服务器已安装Java 8或更高版本
4. **MySQL服务**：确保MySQL服务正在运行

---

详细部署文档请参考：`README-DEPLOY.md`

