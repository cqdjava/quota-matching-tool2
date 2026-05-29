@echo off
REM 定额匹配工具部署和迁移脚本
REM 适用于Windows服务器环境

setlocal

REM 配置变量
set APP_NAME=quota-matching-tool
set APP_JAR=target\%APP_NAME%-0.0.1-SNAPSHOT.jar
set MIGRATION_CLASS=com.enterprise.quota.util.DatabaseMigrationTool

echo === 定额匹配工具部署脚本 ===

REM 检查Java环境
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo 错误: 未找到Java环境
    exit /b 1
)

echo 检查Java版本...
java -version

REM 编译项目
echo 编译项目...
call mvn clean package -DskipTests
if %errorlevel% neq 0 (
    echo 编译失败
    exit /b 1
)

REM 执行数据库迁移
echo 执行数据库迁移...
java -cp "%APP_JAR%" "%MIGRATION_CLASS%"
if %errorlevel% neq 0 (
    echo 数据库迁移失败
    exit /b 1
)

echo 数据库迁移成功

REM 启动应用程序
echo 启动应用程序...
start /b java -jar "%APP_JAR%" > app.log 2>&1

echo 应用程序已启动
echo 日志文件: app.log
echo 部署完成！

pause