@echo off
REM 环境检查脚本 - 检查生产环境配置

echo ========================================
echo 生产环境配置检查
echo ========================================
echo.

REM 检查Java环境
echo [1] 检查Java环境...
where java >nul 2>&1
if %errorlevel% neq 0 (
    echo    ❌ Java未安装或未配置到PATH
) else (
    echo    ✅ Java已安装
    java -version 2>&1 | findstr /i "version"
)
echo.

REM 检查JAR文件
echo [2] 检查JAR文件...
set JAR_FILE=%~dp0target\quota-matching-tool-1.0.0.jar
if exist "%JAR_FILE%" (
    echo    ✅ JAR文件存在: %JAR_FILE%
) else (
    echo    ❌ JAR文件不存在，请先打包
)
echo.

REM 检查MySQL连接（如果MySQL客户端可用）
echo [3] 检查MySQL配置...
if defined DB_HOST (
    echo    数据库主机: %DB_HOST%
) else (
    echo    数据库主机: localhost (默认)
)
if defined DB_PORT (
    echo    数据库端口: %DB_PORT%
) else (
    echo    数据库端口: 3306 (默认)
)
if defined DB_NAME (
    echo    数据库名称: %DB_NAME%
) else (
    echo    数据库名称: quota_db (默认)
)
if defined DB_USERNAME (
    echo    数据库用户: %DB_USERNAME%
) else (
    echo    数据库用户: root (默认)
)
if defined DB_PASSWORD (
    echo    数据库密码: ****** (已设置)
) else (
    echo    ⚠️  数据库密码: 未设置（将使用配置文件中的默认值）
)
echo.

REM 检查端口占用
echo [4] 检查端口8080...
netstat -ano | findstr ":8080" >nul 2>&1
if %errorlevel% equ 0 (
    echo    ⚠️  端口8080已被占用
    echo    占用端口的进程：
    netstat -ano | findstr ":8080"
) else (
    echo    ✅ 端口8080可用
)
echo.

echo ========================================
echo 检查完成
echo ========================================
echo.
echo 提示：
echo   - 如果数据库密码未设置，请先设置环境变量：
echo     set DB_PASSWORD=your_password
echo   - 或修改 application-prod.properties 文件
echo.
pause

