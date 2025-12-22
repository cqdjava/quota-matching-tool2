# 故障排查指南

## 问题：运行脚本后提示"按任意键继续"就退出

### 可能原因

#### 1. 数据库连接失败（最常见）

**症状**: 应用启动后立即退出，提示"按任意键继续"

**原因**: 
- MySQL数据库未启动
- 数据库 `quota_db` 不存在
- 数据库用户名或密码错误
- 数据库密码未配置（生产环境配置中密码为空）

**解决方法**:

**方法一：设置环境变量（推荐）**
```cmd
set DB_USERNAME=root
set DB_PASSWORD=your_password
run-prod.bat
```

**方法二：修改配置文件**
编辑 `src/main/resources/application-prod.properties`，修改：
```properties
spring.datasource.username=root
spring.datasource.password=your_password
```
然后重新打包：
```cmd
mvn clean package -DskipTests
```

**方法三：创建数据库**
```sql
-- 登录MySQL
mysql -u root -p

-- 创建数据库
CREATE DATABASE quota_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

#### 2. 端口被占用

**症状**: 启动失败，提示端口被占用

**解决方法**:
```cmd
# 查看占用端口的进程
netstat -ano | findstr :8080

# 停止占用端口的进程（替换PID为实际进程ID）
taskkill /PID <进程ID> /F

# 或修改端口（编辑 application-prod.properties）
server.port=8081
```

#### 3. Java环境问题

**症状**: 提示"未找到Java环境"

**解决方法**:
- 安装JDK 1.8或更高版本
- 配置JAVA_HOME环境变量
- 将Java添加到PATH

#### 4. JAR文件不存在

**症状**: 提示"JAR文件不存在"

**解决方法**:
```cmd
mvn clean package -DskipTests
```

## 诊断步骤

### 1. 运行环境检查脚本
```cmd
check-env.bat
```

这个脚本会检查：
- Java环境
- JAR文件
- MySQL配置
- 端口占用情况

### 2. 查看详细错误信息

运行脚本时，注意查看上方的错误信息，常见错误：

**数据库连接错误**:
```
com.mysql.cj.jdbc.exceptions.CommunicationsException: 
Communications link failure
```
→ 检查MySQL服务是否启动

```
Access denied for user 'root'@'localhost'
```
→ 检查用户名和密码

```
Unknown database 'quota_db'
```
→ 创建数据库

### 3. 手动测试数据库连接

```cmd
# 测试MySQL连接（如果MySQL客户端可用）
mysql -h localhost -u root -p quota_db
```

### 4. 使用开发环境测试

如果生产环境有问题，先用开发环境测试：
```cmd
run.bat
```

开发环境使用H2内存数据库，不需要MySQL。

## 快速修复

### 如果MySQL未安装或未启动

**选项1**: 使用开发环境（H2内存数据库）
```cmd
run.bat
```

**选项2**: 安装并启动MySQL
```cmd
# 启动MySQL服务
net start MySQL
# 或
net start MySQL80
```

### 如果忘记数据库密码

1. 重置MySQL root密码
2. 或创建新用户：
```sql
CREATE USER 'quota_user'@'localhost' IDENTIFIED BY 'new_password';
GRANT ALL PRIVILEGES ON quota_db.* TO 'quota_user'@'localhost';
FLUSH PRIVILEGES;
```

然后设置环境变量：
```cmd
set DB_USERNAME=quota_user
set DB_PASSWORD=new_password
run-prod.bat
```

## 常见错误代码

| 错误码 | 含义 | 解决方法 |
|--------|------|----------|
| 1 | 一般错误 | 查看上方错误信息 |
| 10061 | 连接被拒绝 | MySQL服务未启动 |
| 1045 | 访问被拒绝 | 用户名或密码错误 |
| 1049 | 未知数据库 | 创建数据库 |
| 10048 | 端口被占用 | 停止占用进程或修改端口 |

## 获取帮助

如果问题仍未解决：

1. 查看应用日志（如果已生成）
2. 运行 `check-env.bat` 检查环境
3. 查看上方的详细错误信息
4. 检查 `application-prod.properties` 配置

