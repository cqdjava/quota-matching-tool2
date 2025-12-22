# 运行脚本使用说明

## 📋 脚本列表

### Windows 脚本

1. **run.bat** - 开发/生产环境运行脚本（默认开发环境）
2. **run-prod.bat** - 生产环境运行脚本

### Linux 脚本

1. **run.sh** - 开发/生产环境运行脚本（默认开发环境）
2. **run-prod.sh** - 生产环境运行脚本
3. **start.sh** - 后台启动脚本（生产环境推荐）
4. **stop.sh** - 停止脚本

## 🚀 快速开始

### Windows 系统

#### 开发环境运行
```cmd
run.bat
```
或
```cmd
run.bat dev
```

#### 生产环境运行
```cmd
run-prod.bat
```
或
```cmd
run.bat prod
```

### Linux 系统

#### 开发环境运行
```bash
chmod +x run.sh
./run.sh
```
或
```bash
./run.sh dev
```

#### 生产环境运行（前台）
```bash
chmod +x run-prod.sh
./run-prod.sh
```
或
```bash
./run.sh prod
```

#### 生产环境运行（后台，推荐）
```bash
chmod +x start.sh stop.sh
./start.sh
```

## 📝 脚本说明

### run.bat / run.sh

**功能**: 前台运行应用，适合开发和测试

**特点**:
- 直接在前台运行，可以看到实时日志
- 按 Ctrl+C 可以停止应用
- 默认使用开发环境配置（H2内存数据库）

**使用场景**:
- 本地开发测试
- 快速验证功能

### run-prod.bat / run-prod.sh

**功能**: 使用生产环境配置前台运行

**特点**:
- 使用生产环境配置（MySQL数据库）
- 前台运行，可以看到实时日志
- 适合生产环境调试

**使用场景**:
- 生产环境调试
- 查看启动日志

### start.sh

**功能**: 后台启动应用（生产环境推荐）

**特点**:
- 后台运行，不占用终端
- 自动创建日志文件
- 支持进程管理（PID文件）
- 自动配置JVM参数
- 支持环境变量配置

**使用场景**:
- 生产环境部署
- 服务器长期运行

**相关命令**:
```bash
# 启动
./start.sh

# 停止
./stop.sh

# 查看日志
tail -f logs/application.log

# 查看进程
ps aux | grep quota-matching-tool
```

## ⚙️ 环境变量配置

### Windows

在运行脚本前设置环境变量：
```cmd
set DB_HOST=localhost
set DB_PORT=3306
set DB_NAME=quota_db
set DB_USERNAME=root
set DB_PASSWORD=your_password
run-prod.bat
```

### Linux

在运行脚本前设置环境变量：
```bash
export DB_HOST=localhost
export DB_PORT=3306
export DB_NAME=quota_db
export DB_USERNAME=root
export DB_PASSWORD=your_password
./run-prod.sh
```

或在 `start.sh` 中配置环境变量。

## 🔧 配置说明

### 开发环境配置

- **数据库**: H2内存数据库（应用重启后数据会丢失）
- **日志级别**: DEBUG
- **端口**: 8080
- **H2控制台**: 启用（http://localhost:8080/h2-console）

### 生产环境配置

- **数据库**: MySQL（需要先创建数据库）
- **日志级别**: INFO
- **端口**: 8080
- **日志文件**: `/var/log/quota-matching-tool/application.log`
- **H2控制台**: 禁用

## 📊 运行模式对比

| 特性 | run.bat/run.sh | start.sh |
|------|----------------|----------|
| 运行方式 | 前台 | 后台 |
| 日志输出 | 控制台 | 文件 |
| 进程管理 | 手动 | 自动（PID文件） |
| 适合场景 | 开发/测试 | 生产环境 |
| 停止方式 | Ctrl+C | ./stop.sh |

## 🐛 常见问题

### 1. JAR文件不存在

**错误**: `JAR文件不存在`

**解决**: 先执行打包命令
```bash
mvn clean package -DskipTests
```

### 2. Java环境未找到

**错误**: `未找到Java环境`

**解决**: 
- Windows: 安装JDK并配置环境变量
- Linux: `sudo apt-get install openjdk-8-jdk` 或 `sudo yum install java-1.8.0-openjdk`

### 3. 端口被占用

**错误**: `端口8080已被占用`

**解决**: 
- 修改 `application.properties` 中的端口
- 或停止占用端口的进程

### 4. 数据库连接失败

**错误**: `数据库连接失败`

**解决**: 
- 检查MySQL服务是否启动
- 检查数据库配置是否正确
- 检查数据库是否存在

## 📞 更多信息

详细部署说明请参考 `DEPLOY.md`

