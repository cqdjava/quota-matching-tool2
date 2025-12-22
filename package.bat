@echo off
REM 打包脚本 - Windows版本
REM 使用方法: package.bat

echo ==========================================
echo 开始打包应用...
echo ==========================================

REM 清理旧的构建
echo 清理旧的构建文件...
call mvn clean

REM 打包（跳过测试，加快打包速度）
echo 开始Maven打包...
call mvn package -DskipTests

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ==========================================
    echo 打包成功!
    echo ==========================================
    echo JAR文件位置: target\quota-matching-tool-1.0.0.jar
    echo.
    dir target\quota-matching-tool-1.0.0.jar
    echo.
    echo ==========================================
    echo 打包完成，可以部署到云服务器了！
    echo ==========================================
) else (
    echo.
    echo ==========================================
    echo 打包失败，请检查错误信息！
    echo ==========================================
    exit /b 1
)

pause

